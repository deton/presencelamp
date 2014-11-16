# 会議室残り時間通知LED

会議室を使える残り時間が15分以下になったことをLED点滅で通知する、
会議室設置用デバイスです。
Wi-Fi接続したIntel Edisonにより、会議室予約情報をExchangeサーバから取得し、
LEDを点滅させます。

以下のような状況を改善するのが目的です。

+ 会議室に次の予約が入っている時刻が近づいているのに気づかずに
  議論に集中していたため、議論途中であわてて会議室を空けないといけなくなった
+ 開始時刻になったのに、前の会議が終わっていなくて会議を始められない

会議室内に置いておくと、
次の会議予約が入っている時刻が近づいたときにLEDが点滅するので、
間もなく会議室を空けないといけないことに気づきやすくなります。

![PresenceLamp写真](https://github.com/deton/presencelamp/raw/master/PresenceLamp.jpg)

## 機能
* 15分間のカラータイマー。
  会議室の次の予約が入っている時刻が近づいていることを通知。
 * 最初の10分間は黄色点滅。点滅間隔が2秒間隔から短くなっていく。
 * 最後の5分間は赤色点滅(黄色と赤色を交互に点滅)。
 * 15分経過後(予約開始時刻以降)は、赤点灯。リマインドのため、3秒おきに赤点滅。
 * さらに5分経過後、消灯。

## 構成

LED --- presencelamp(Edison) --- MeetingRoomServlet --- Exchangeサーバ

### LED点滅制御(デバイス側。presencelamp)
5分間隔で、サーバ上の会議室予約状況取得Servletにリクエストを送り、
次の予約が入っている時刻が近づいているかチェックします。
次の予約開始時刻の15分前になったら、LEDの点滅を開始します。

LED点滅はLinux GPIO sysfs(/sys/class/gpio/)を使って制御しています。

### 会議室予約状況取得Servlet(サーバ側。MeetingRoomServlet)
[EWS Java API](https://github.com/OfficeDev/ews-java-api)を使って、
Exchangeサーバから会議室の予約状況を取得します。

パスワードをデバイス側に置きたくなかったので、
サーバ側からEWS Java APIを使う形にしました。

サーバ側で既に動いているTomcat上で動かすため、Servletとして作成。

### ダミーサーバ(デバイス側。dummyserver)
LED点滅制御の動作確認用サーバ。
MeetingRoomServletでは、Exchangeサーバが必要なのと、
15分は長いので、短時間(1.5分)で動作を確認したい場合用。

```
./dummyserver &
./presencelamp -url http://localhost:8080
```

## 配備
### デバイス側(presencelamp)
#### Edisonの初期設定
```
configure_edison --wifi
configure_edison --password
vi /etc/systemd/timesyncd.conf # 社内NTPサーバを追加
timedatectl set-timezone Asia/Tokyo
vi /etc/systemd/journald.conf # Storage=volatileに変更
```

journald.confの変更は、disk full回避のためです。
再起動のたびに/var/log/journal/以下が増えていくようなので。
[参考](http://nonnoise.github.io/Edison/NoSpace.html)

#### presencelampの配備
別マシン上のGo言語コンパイラでlinux/386用にビルドして、
実行ファイルをscp等でEdisonにコピー。

```
go get github.com/aqua/raspberrypi/gpio
GOOS=linux GOARCH=386 go build presencelamp.go
```

Edisonの電源投入時に自動起動するように、
presencelamp.serviceファイルを、/etc/systemd/system/にコピーして、
`systemctl enable presencelamp`

presencelamp.serviceファイル内の-room引数や-url引数の値は変更が必要です。

コマンドライン引数 | 意味                           | 例
------------------ | ------------------------------ | -------------------
`-room`            | 会議室のアドレス               | room001@example.com
`-url`             | 会議室予約状況取得ServletのURL | http://10.25.254.23:8080/MeetingRoomServlet/meetingroom
`-pinred`          | 赤色LEDのGPIO番号              | 165
`-pinyellow`       | 黄色LEDのGPIO番号              | 15

### サーバ側(MeetingRoomServlet)
パスワードや、ユーザID、Exchangeサーバホスト名は、
LocalProperties.javaにあるので変更してください。

ビルド時は、EWS Java APIのjarをlib/に置いてください。

gradle warでビルドしたwarファイルを、tomcat等に配備。

## 部品
+ Intel Edison + Breakout Board
+ LED [黄色](http://www.sengoku.co.jp/mod/sgk_cart/detail.php?code=EEHD-0RUU)、
  [赤色](http://www.sengoku.co.jp/mod/sgk_cart/detail.php?code=EEHD-4KL3)
+ トランジスタ2SC1815*2
+ 抵抗 220Ω*2
+ 抵抗 1kΩ*4
+ ハサミで切れる薄型ユニバーサル基板(UB-THN01)
+ 丸ピンソケット*4

回路は、[Intel Edison と ruby で Lチカ](http://qiita.com/rerofumi/items/b6cbbd382f67ca19e4ce)
とほぼ同じです。
LEDは赤と黄だけなので、Vccを5Vでなく3.3Vピンから取るようにして、
抵抗を330Ωから220Ωに変更しています
(抵抗は変えなくても良かったかも)。
(1kΩの抵抗がなかったので近い値のもので代用しています)。

+ J19-3 --- GND
+ J20-2 --- 3.3V
+ J18-2 --- GPIO165 赤色LED
+ J20-7 --- GPIO15  黄色LED

LEDはそのままだとまぶしいのと、角度によってはわかりにくいので、
紙ケースをかぶせて光を拡散させています。

![内部写真](https://github.com/deton/presencelamp/raw/master/PresenceLampInside.jpg)

Breakout boardの使用するピンに、丸ピンソケットをはんだづけしておいて、
そのピン位置に合わせて抵抗やLEDをユニバーサル基板上に配置して、
切らずに残した足を丸ピンソケットにさしています。

## 拡張案
* 会議室内向けプレゼンス表示
 * 会議室関係
      * 次の予約をしている人が会議室前まで来たら通知
      * 次の予約をしている人が、早く終われと言う操作をすると、会議室内に通知。
        (開始時刻になっているのに前の会議がまだ終わらない場合)
      * 予約してないけど会議したいので、可能ならば空けて欲しい、
        という人がいることを通知
      * 今の予約の終了時刻通知(次の予約の開始時刻通知でなく)。会議短縮目的。
      * 「お静かに」(声が大きいのでもう少し抑えて欲しい)と思っている人が
        周りにいることを通知。
        (オフィスの席と隣接した、壁で囲われていない会議スペースにおいて、
        近くの人が、会議の声がうるさいと感じた時にWebで入力)
      * 昼休み時間や終業時間が近づいていることを通知
 * 会議の空気表示
      * 話についていけなくなった/質問がある/いいね/へぇ/拍手などを、
        Webで入力するとLED点灯。
      * 会議参加者の意見集約表示。Webで入力された各参加者の賛成/反対を集計し、
        賛成が過半数かどうか表示。
* 会議室外向けプレゼンス表示
 * 会議室が使用中かどうか
* 入出力部品の追加
 * 音などによる通知。目覚まし時計や予鈴と同様。
   (より気づきやすい通知をするには、
   [XFD(eXtreme Feedback Device)](http://objectclub.jp/community/xfd/)を参考に?)
 * 照明による通知(Philips hue等)
 * 時計表示LCDの追加。時計としても使えるように。
 * フルカラーLED化。様々なプレゼンス表示用
 * LED消灯ボタン。目覚まし時計と同様に。
 * 予約延長ボタンや次週の同じ時間予約ボタン等。
* デバイスとしては、会議室用タブレットを置く形が一番便利な気も。
  予約時間の延長や、次回会議の予約等もタブレット上で操作する等。
  ただ、会議室用にタブレットやスマホを置くのは、
  勝手に持ち去られることへの対策が必要になって面倒。

## 関連
* [PresenceStick](https://github.com/deton/presencestick)。
  各人の次の予定が近づいた時にLED点滅によりプレゼンス表示。
  (各人の持ち込むノートPCにUSB接続)

## Raspberry Pi版
USB Wi-FiでのWPA2 Enterprise(EAP)への接続が安定せず断念。

![PresenceLamp写真Raspberry Pi版](https://github.com/deton/presencelamp/raw/master/PresenceLampRasPi.jpg)

+ IO DATA WN-G150UM。5時間ぐらいで切断される場合あり。
* ELECOM WDC-150SU2M。ドライバのコンパイル要。
  接続後、3時間ぐらいすると切断されている。再度ifupしないと接続されない。
* BUFFLAO WLI-UC-GNM。接続できず。iwlist scanでAPリストは出てくる。
* IO DATA WN-AC433UK。ドライバのコンパイル要。接続できず。

### 部品
+ Raspberry Pi Model B
+ USB Wi-Fi WN-G150UM
+ LED [黄色](http://www.sengoku.co.jp/mod/sgk_cart/detail.php?code=EEHD-0RUU)、
  [赤色](http://www.sengoku.co.jp/mod/sgk_cart/detail.php?code=EEHD-4KL3)
+ 抵抗 240Ω*2
+ ピンソケット2口*2
+ ハサミで切れる薄型ユニバーサル基板(UB-THN01)。
  両面を使いたかったので重ねて使用。

![内部写真Raspberry Pi版](https://github.com/deton/presencelamp/raw/master/PresenceLampRasPiInside.jpg)

golangソースはEdison版と同じ。GOARCH=armでビルド。

## その他細かい話
+ もともと、EWS Java APIをデバイス側で使う必要があるかと思って、
EdisonやRaspberry Piを候補にしていましたが、
Javaはサーバ側で使って、デバイス側からはHTTP通信だけするなら、
Arudino等を使ってもいいかもしれません。
有線LANだと会議室内への設置が面倒なので無線LANにはしたいところです。
 + (Wi-Fiシールド付きArduinoとしてのIRKitも候補ですが、
   WPA2 Enterpriseに対応するには、
   NDAを結んでWPA2 Enterprise対応のWiFiモジュール用ファームを
   入手する必要がありそうなので面倒。
   [参考](http://www.sugakoubou.com/doku/gs-wifi#app_fw))

+ LED点灯が目的なら、Edison Arduino expansion boardの方が、
  IOが3.3Vなので使いやすそうですが、サイズが大きいので不採用。
+ 赤色LEDは、(鈴商あたりで売っている)低電流対応のものなら、
  1.8V 3mAでも使えなくはなさそうですが、
  今回は明るさが欲しかったのでトランジスタを使用。

+ Linux GPIO sysfsを制御するgolang用ライブラリとして、
github.com/kidoman/embdを試しましたが、CTRL-Cで終了した後など、
/sys/class/gpio/gpio24等のファイルが残っていると初期化エラーになって面倒なのと、
細かいファイルで構成されていて把握しにくかったので、
github.com/aqua/raspberrypi/gpioに変更。

+ Exchangeサーバ用パスワードは暗号化して持つ形にしたい気も。
plain textファイルで置くとうっかり見れてしまう恐れがあるので、
classファイルにしていますが、生のまま入っているのは同じなのであまり良くない。
ただ、暗号化したとしても、復号できる形で持っておく必要はあるので、
うっかり見るのを回避できる程度。ということもあって、現状の形にしています。

+ もう少し余裕を持って部品を配置した方が良かった。
  特にLEDが近すぎて、隣のLEDによる影ができてしまってきれいに光が広がらない。
