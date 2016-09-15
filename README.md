# GoogleDriveUploder
GoogleDriveに指定のファイルをアップロードするCLIツール

使い方
------

### GoogleAPIのクライアントID、クライアントシークレットの取得

1. GoogleAPIsに以下からログイン  
https://console.developers.google.com/project  
1. プロジェクトを作成  
プロジェクト名は適当でよい。  
1. Google Drive APIを有効にする  
追加したプロジェクトを選択→APIを有効にする→検索窓にDriveと入れる→Google Drive APIを選択→有効にする  
1. クライアントID、クライアントシークレットを取得  
認証情報→認証情報を作成→ウィザードで選択→使用するAPI=Google Drive API、APIを呼び出す場所=その他のUI(Windows、CLIツールなど)、アクセスするデータの種類=ユーザデータ

### ビルド

1. プロジェクトをclone
1. client_secrets.jsonに値を入れる  
src/main/resources以下にあるclient_secrets.jsonを取ってきて、取得したクライアントID、クライアントシークレットを入れる。  
1. プロジェクトのルートディレクトリ直下でmvn packageを実行  
target以下のGoogleDriveUploader-jar-with-dependencies.jarを使う。

### 認証情報の作成

* GUI環境でjarを実行し認証情報(StoredCredential)を作成する  
java -jar GoogleDriveUploader.jar hoge hoge hogeでOK(引数は適当でよい)

### 成果物の配置

* jar, StoredCredentialをCUI環境に転送  
jarは適当なところでよい。ここでは/home/hogehoge/bat/とする。  
StoredCredentialはホームディレクトリの.store/google_drive_uploader/StoredCredentialにあるので、同じパスとなるようホームディレクトリの下に.store/google_drive_uploaderを作って配置する。

### 実行方法

* jarの第一引数にアップロードするファイルを、第2引数にアップロード先ディレクトリを、第3引数にアップロード先ファイル名を指定する。  
cronで定期的に実行する例(毎日0時5分に動かし、アップロード時にファイル名末尾に日付を付与する場合)  
    5 0 * * * cd /home/hogehoge/bat/;java -jar GoogleDriveUploader.jar hoge.zip /backup/piyo hoge\`date +\%y\%m\%d\`.zip
