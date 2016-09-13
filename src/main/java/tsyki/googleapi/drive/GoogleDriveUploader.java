package tsyki.googleapi.drive;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;

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
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

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

    public static void main( String[] args) {
        Preconditions.checkArgument( args.length == 2, "引数1にアップロードするファイルパスを、引数2にアップロード先ディレクトリを指定してください。");

        String srcFilePath = args[0];
        String destDir = args[1];

        try {

            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            dataStoreFactory = new FileDataStoreFactory( DATA_STORE_DIR);
            // authorization
            Credential credential = authorize();
            // set up the global Drive instance
            drive = new Drive.Builder( httpTransport, JSON_FACTORY, credential).setApplicationName( APPLICATION_NAME).build();

            // run commands
            View.header1( "startUpload: src=" + srcFilePath + " dest=" + destDir);
            @SuppressWarnings( "unused")
            File uploadedFile = uploadFile( srcFilePath, destDir);

            View.header1( "Success!");
            return;
        }
        catch ( IOException e) {
            e.printStackTrace();
        }
        catch ( Throwable t) {
            t.printStackTrace();
        }
        System.exit( 1);
    }

    /** Uploads a file using either resumable or direct media upload. */
    private static File uploadFile( String srcFilePath, String destDir) throws IOException {
        File fileMetadata = new File();
        final java.io.File uploadFile = new java.io.File( srcFilePath);
        fileMetadata.setTitle( uploadFile.getName());

        // TODO typeは常にzipでよいのか？
        FileContent mediaContent = new FileContent( "application/zip", uploadFile);

        Drive.Files.Insert insert = drive.files().insert( fileMetadata, mediaContent);

        // TODO destDirにアップロードする
        // FileList result = drive.files().list().setMaxResults(100).execute();
        // List<File> files = result.getItems();

        MediaHttpUploader uploader = insert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled( true);
        uploader.setProgressListener( new FileUploadProgressListener());
        return insert.execute();
    }

}
