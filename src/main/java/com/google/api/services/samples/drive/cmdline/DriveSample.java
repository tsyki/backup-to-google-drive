/*
 * Copyright (c) 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.services.samples.drive.cmdline;

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
 * A sample application that runs multiple requests against the Drive API. The requests this sample
 * makes are:
 * <ul>
 * <li>Does a resumable media upload</li>
 * <li>Updates the uploaded file by renaming it</li>
 * <li>Does a resumable media download</li>
 * <li>Does a direct media upload</li>
 * <li>Does a direct media download</li>
 * </ul>
 * @author rmistry@google.com (Ravi Mistry)
 */
public class DriveSample {

    /**
     * Be sure to specify the name of your application. If the application name is {@code null} or
     * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "BackupToDrive";

    /** Directory to store user credentials. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File( System.getProperty( "user.home"), ".store/drive_sample");

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
            new InputStreamReader( DriveSample.class.getResourceAsStream( "/client_secrets.json")));
        if ( clientSecrets.getDetails().getClientId().startsWith( "Enter") || clientSecrets.getDetails().getClientSecret().startsWith( "Enter ")) {
            System.out.println( "Enter Client ID and Secret from https://code.google.com/apis/console/?api=drive "
                    + "into drive-cmdline-sample/src/main/resources/client_secrets.json");
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
            System.err.println( e.getMessage());
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

    /** Updates the name of the uploaded file to have a "drivetest-" prefix. */
    // private static File updateFileWithTestSuffix(String id) throws IOException {
    // File fileMetadata = new File();
    // fileMetadata.setTitle("drivetest-" + UPLOAD_FILE.getName());
    //
    // Drive.Files.Update update = drive.files().update(id, fileMetadata);
    // return update.execute();
    // }

    /** Downloads a file using either resumable or direct media download. */
    // private static void downloadFile(boolean useDirectDownload, File uploadedFile) throws
    // IOException {
    // // create parent directory (if necessary)
    // java.io.File parentDir = new java.io.File(DIR_FOR_DOWNLOADS);
    // if (!parentDir.exists() && !parentDir.mkdirs()) {
    // throw new IOException("Unable to create parent directory");
    // }
    // OutputStream out = new FileOutputStream(new java.io.File(parentDir, uploadedFile.getTitle()));
    //
    // MediaHttpDownloader downloader =
    // new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
    // downloader.setDirectDownloadEnabled(useDirectDownload);
    // downloader.setProgressListener(new FileDownloadProgressListener());
    // downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
    // }
}
