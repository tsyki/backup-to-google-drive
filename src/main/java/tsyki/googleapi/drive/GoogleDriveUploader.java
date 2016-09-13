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

        try {

            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            dataStoreFactory = new FileDataStoreFactory( DATA_STORE_DIR);
            // authorization
            Credential credential = authorize();
            // set up the global Drive instance
            drive = new Drive.Builder( httpTransport, JSON_FACTORY, credential).setApplicationName( APPLICATION_NAME).build();

            // run commands
            View.header1( "startUpload: src=" + srcFilePath + " dest=" + destDirPath);
            @SuppressWarnings( "unused")
            File uploadedFile = uploadFile( srcFilePath, destDirPath);

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
        Preconditions.checkArgument( args.length == 2, "引数1にアップロードするファイルパスを、引数2にアップロード先ディレクトリを指定してください。");
        String srcFilePath = args[0];
        String destDir = args[1];
        GoogleDriveUploader uploader = new GoogleDriveUploader();
        uploader.upload( srcFilePath, destDir);
        System.exit( 1);

    }

    /** Uploads a file using either resumable or direct media upload. */
    private File uploadFile( String srcFilePath, String destDirPath) throws IOException {
        File destDir = getDir( destDirPath, true);

        File fileMetadata = new File();
        final java.io.File uploadFile = new java.io.File( srcFilePath);
        fileMetadata.setTitle( uploadFile.getName());
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
        List<File> children = retrieveAllFiles( drive.files().list().setQ( "trashed = false"));
        String[] dirNames = dirPath.split( "/");
        File parent = null;
        for ( int currentDirIdx = 0; currentDirIdx < dirNames.length; currentDirIdx++) {
            String dirName = dirNames[currentDirIdx];
            // dirPathの最初か最後に/があると空文字が来てしまうのでそれは無視
            if ( dirName == null || dirName.isEmpty()) {
                continue;
            }
            File nextDir = null;
            if ( parent != null) {
                children = retrieveAllFiles( drive.files().list().setQ( "'" + parent.getId() + "' in parents"));
            }
            for ( File file : children) {
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
            }
        }
        while ( request.getPageToken() != null && request.getPageToken().length() > 0);

        return result;
    }
}
