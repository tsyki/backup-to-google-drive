# GoogleDriveUploder
GoogleDriveに指定のファイルをアップロードするCLIツール

使い方
------

1. GoogleAPIsに以下からログイン  
https://console.developers.google.com/project  
1. プロジェクトを作成  
プロジェクト名は適当でよい。  
1. Google Drive APIを有効にする  
追加したプロジェクトを選択→APIを有効にする→検索窓にDriveと入れる→Google Drive APIを選択→有効にする  
1. アクセストークンを取得  
TODO  
1. jarを作成  
cloneした後、EclipseでインポートしてRunnable JAR fileでjarを生成  
1. client_secrets.jsonに値を入れる  
取得したアクセストークンを入れて、jarと同じディレクトリに置く  
1. GUI環境でjarを実行。認証する  
java -jar GoogleDriveUploader.jarでOK  
1. jar, client_secrets.json, StoredCredentialをCUI環境に転送  
jar,client_secrets.jsonは適当なところで。ここでは/home/hogehoge/bat/とする  
StoredCredentialはホームディレクトリの.store/google_drive_uploader/StoredCredentialにあるので、同じパスとなるようホームディレクトリの下に.store/google_drive_uploaderを作って配置  
1. CUI環境でcronで定期的に実行(毎日0時5分に動かす例)  
jarの第一引数にアップロードするファイルを、第2引数にアップロード先ディレクトリを指定する  
    5 0 * * * cd /home/hogehoge/bat/;java -jar GoogleDriveUploader hoge.zip destDirName
