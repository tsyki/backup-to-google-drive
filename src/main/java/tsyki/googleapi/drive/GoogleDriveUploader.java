package tsyki.googleapi.drive;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

/**
 * 指定ファイルをGoogle DriveへアップロードするためのCLIツール
 * @author TOSHIYUKI.IMAIZUMI
 * @since 2016/09/13
 */
public class GoogleDriveUploader {

    /**
     * Be sure to specify the name of your application. If the application name is {@code null} or
     * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "BackupToDrive";

    /** 認証情報を保存するパス */
    private static final java.io.File DATA_STORE_DIR = new java.io.File( System.getProperty( "user.home"), ".store/google_drive_uploader");

    /**
     * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
     * globally shared instance across your application.
     */
    private static FileDataStoreFactory dataStoreFactory;

    /** Global instance of the HTTP transport. */
    private static HttpTransport httpTransport;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Global Drive API client. */
    private static Drive drive;

    /** アップロード先のディレクトリ指定時のデリミタ */
    private static final String FILE_DELIMITER = "/";

    /** Authorizes the installed application to access user's protected data. */
    private static Credential authorize() throws Exception {
        // load client secrets
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load( JSON_FACTORY,
            new InputStreamReader( GoogleDriveUploader.class.getResourceAsStream( "/client_secrets.json")));
        if ( clientSecrets.getDetails().getClientId().startsWith( "Enter") || clientSecrets.getDetails().getClientSecret().startsWith( "Enter ")) {
            System.out.println( "client_secrets.jsonにClient IDとClient Secretが設定されていません。");
            System.exit( 1);
        }
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder( httpTransport, JSON_FACTORY, clientSecrets,
            Collections.singleton( DriveScopes.DRIVE_FILE)).setDataStoreFactory( dataStoreFactory).build();
        // authorize
        return new AuthorizationCodeInstalledApp( flow, new LocalServerReceiver()).authorize( "user");
    }

    public void upload( String srcFilePath, String destDirPath) {
        upload( srcFilePath, destDirPath, srcFilePath);
    }

    /**
     * @param srcFilePath アップロードするファイルパス
     * @param destDir アップロード先のパス。hoge/piyo/fugaのように指定
     * @param destFileName アップロード先のファイル名
     */
    public void upload( String srcFilePath, String destDir, String destFileName) {

        try {

            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            dataStoreFactory = new FileDataStoreFactory( DATA_STORE_DIR);
            // authorization
            Credential credential = authorize();
            // set up the global Drive instance
            drive = new Drive.Builder( httpTransport, JSON_FACTORY, credential).setApplicationName( APPLICATION_NAME).build();

            // run commands
            View.header1( "startUpload: src=" + srcFilePath + " destDir=" + destDir + "destFileName=" + destFileName);
            @SuppressWarnings( "unused")
            File uploadedFile = uploadFile( srcFilePath, destDir, destFileName);

            View.header1( "Success!");
            return;
        }
        catch ( IOException e) {
            e.printStackTrace();
        }
        catch ( Throwable t) {
            t.printStackTrace();
        }

    }

    public static void main( String[] args) {
        Preconditions.checkArgument( args.length == 3,
            "引数1にアップロード元ファイルパスを、引数2にアップロード先ディレクトリを、引数3にアップロード先ファイル名を指定してください。例:java -jar GoogleDriveUploader.jar hoge.txt backup/piyo hoge_bk.txt");
        String srcFilePath = args[0];
        String destDir = args[1];
        String uploadFileName = args[2];
        GoogleDriveUploader uploader = new GoogleDriveUploader();
        uploader.upload( srcFilePath, destDir, uploadFileName);
        System.exit( 1);
    }

    /** Uploads a file using either resumable or direct media upload. */
    private File uploadFile( String srcFilePath, String destDirPath, String uploadFileName) throws IOException {
        File destDir = getDir( destDirPath, true);

        File fileMetadata = new File();
        final java.io.File uploadFile = new java.io.File( srcFilePath);
        fileMetadata.setTitle( uploadFileName);
        if ( destDir != null) {
            fileMetadata.setParents( createParentRef( destDir));
        }
        // TODO typeは常にzipでよいのか？
        FileContent mediaContent = new FileContent( "application/zip", uploadFile);

        Drive.Files.Insert insert = drive.files().insert( fileMetadata, mediaContent);

        MediaHttpUploader uploader = insert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled( true);
        uploader.setProgressListener( new FileUploadProgressListener());
        return insert.execute();
    }

    /**
     * 指定のディレクトリを返す
     * @param destDir 「hoge/piyo/fuga」のような/区切りのパス
     * @param createIfNotExists 指定のディレクトリが見つからない場合に作成するか
     * @return
     * @throws IOException
     */
    private File getDir( String dirPath, boolean createIfNotExists) throws IOException {
        String[] dirNames = dirPath.split( FILE_DELIMITER);
        File parent = null;
        for ( int currentDirIdx = 0; currentDirIdx < dirNames.length; currentDirIdx++) {
            String dirName = dirNames[currentDirIdx];
            // dirPathの最初か最後に/があると空文字が来てしまうのでそれは無視
            if ( dirName == null || dirName.isEmpty()) {
                continue;
            }
            File nextDir = null;
            List<File> children;
            if ( parent != null) {
                // 有効かつparent直下のファイルを全て取得
                children = retrieveAllFiles( drive.files().list().setQ( "trashed = false and '" + parent.getId() + "' in parents"));
            }
            else {
                // 初回はルートディレクトリ直下の有効なファイルを全て取得
                children = retrieveAllFiles( drive.files().list().setQ( "trashed = false"));
            }
            for ( File file : children) {
                // NOTE ディレクトリ名重複は考えず最初にヒットしたものと使う
                if ( file.getTitle().equals( dirName)) {
                    nextDir = file;
                    break;
                }
            }
            // ヒットしたら次へ
            if ( nextDir != null) {
                parent = nextDir;
                continue;
            }
            // 指定のディレクトリが見つからなかった
            if ( !createIfNotExists) {
                return null;
            }
            // 無ければ新規追加
            File body = new File();
            body.setTitle( dirName);
            body.setMimeType( "application/vnd.google-apps.folder");
            if ( parent != null) {
                body.setParents( createParentRef( parent));
            }
            nextDir = drive.files().insert( body).execute();
            parent = nextDir;
        }
        return parent;

    }

    private List<ParentReference> createParentRef( File parent) {
        return Arrays.asList( new ParentReference().setId( parent.getId()));
    }

    private static List<File> retrieveAllFiles( Files.List request) throws IOException {
        List<File> result = new ArrayList<File>();

        do {
            try {
                FileList files = request.execute();
                result.addAll( files.getItems());
                request.setPageToken( files.getNextPageToken());
            }
            catch ( IOException e) {
                System.out.println( "An error occurred: " + e);
                request.setPageToken( null);
                throw e;
            }
        }
        while ( request.getPageToken() != null && request.getPageToken().length() > 0);

        return result;
    }
}
