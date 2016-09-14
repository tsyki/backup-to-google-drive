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
1. クライアントID、クライアントシークレットを取得  
認証情報→認証情報を作成→ウィザードで選択→使用するAPI=Google Drive API、APIを呼び出す場所=その他のUI(Windows、CLIツールなど)、アクセスするデータの種類=ユーザデータ
1. jarを作成  
cloneした後、EclipseでインポートしてRunnable JAR fileでjarを生成  
1. client_secrets.jsonに値を入れる  
取得したクライアントID、クライアントシークレットを入れて、jarと同じディレクトリに置く  
1. GUI環境でjarを実行し認証情報(StoredCredential)を作成する  
java -jar GoogleDriveUploader.jar hoge hoge hogeでOK(引数は適当でよい)  
1. jar, client_secrets.json, StoredCredentialをCUI環境に転送  
jar,client_secrets.jsonは適当なところで。ここでは/home/hogehoge/bat/とする  
StoredCredentialはホームディレクトリの.store/google_drive_uploader/StoredCredentialにあるので、同じパスとなるようホームディレクトリの下に.store/google_drive_uploaderを作って配置  
1. CUI環境でcronで定期的に実行(毎日0時5分に動かす例)  
jarの第一引数にアップロードするファイルを、第2引数にアップロード先ディレクトリを、第3引数にアップロード先ファイル名を指定する  
    5 0 * * * cd /home/hogehoge/bat/;java -jar GoogleDriveUploader hoge.zip /backup/piyo hoge`date +\%y\%m\%d`.zip