package jp.ac.shohoku.teamu.gakuseiread4;

import androidx.appcompat.app.AppCompatActivity;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Formatter;

public class MainActivity extends AppCompatActivity {

    //Widgetの宣言
    TextView txt_idm;
    TextView txt_pmm;
    TextView txt_waonno;
    Button btn_start;
    Button btn_stop;

    //NfcAdapterの宣言
    NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Widgetの初期化
        txt_idm = findViewById(R.id.txt_idm);
        txt_pmm = findViewById(R.id.txt_pmm);
        txt_waonno = findViewById(R.id.txt_waonno);
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);

        //初期設定（トグル）
        btn_stop.setEnabled(false);

        //NfcAdapterの初期化
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        //onClickイベントの設定
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //トグル設定
                btn_start.setEnabled(false);
                btn_stop.setEnabled(true);

                //TextView初期化
                txt_idm.setText("");
                txt_pmm.setText("");
                txt_waonno.setText("");

                //ReaderMode On
                nfcAdapter.enableReaderMode(MainActivity.this,new MyReaderCallback(),NfcAdapter.FLAG_READER_NFC_F,null);

            }
        });

        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //トグル設定
                btn_start.setEnabled(true);
                btn_stop.setEnabled(false);

                //ReaderMode Off
                nfcAdapter.disableReaderMode(MainActivity.this);

            }
        });

    }

    //Callback用 inner class記述
    private class MyReaderCallback implements NfcAdapter.ReaderCallback{
        @Override
        public void onTagDiscovered(Tag tag){

            //タグが見つかったらとりあえずログ出力
            Log.d("Hoge","Tag Discovered!");

            //FeliCaと通信するためのNfcFを初期化
            NfcF nfc = NfcF.get(tag);

            try{

                nfc.connect();

                //とりあえずいろいろFeliCaの生コマンドで制御。idmとかならtag.getId()とかで取れる。

                //pollingコマンド（FeliCa生コマンド） 共通領域のシステムコード0xFE 0x00を指定。
                byte[] polling_request = {(byte)0x06,(byte)0x00,(byte)0xFE,(byte)0x00,(byte)0x00,(byte)0x00};
                //response受け取り用byte配列

                //コマンド送信・受信
                byte[] polling_response = nfc.transceive(polling_request);

                //idmの取り出し
                byte[] idm =tag.getId();
                //pmmの取り出し（ついで）
                byte[] pmm = Arrays.copyOfRange(polling_response,11,19);

                //byte列を文字列に変換
                final String idmString = bytesToHexString(idm);
                final String pmmString = bytesToHexString(pmm);

                //waonno処理

                //waonnno request
                //カスタム関数をつかってrequestコマンドを組み立て
                byte[] waonno_request = readWithoutEncryption(idm,2);
                //コマンド送信・受信
                byte[] wannno_response = nfc.transceive(waonno_request);

                //WAON番号部分を切り取り
                byte[] waonno = Arrays.copyOfRange(wannno_response,13,19);

                //文字列変換
                //final String waonnoString = bytesToHexString(waonno);

                //ASCⅡコードへ変換(学籍番号はASCⅡコードで書かれているため)
                final String waonnoString = new String(waonno, "US-ASCII");

                //親スレッドのUI更新
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txt_idm.setText(idmString);
                        txt_pmm.setText(pmmString);
                        txt_waonno.setText(waonnoString);
                    }
                });

                nfc.close();

            }catch(Exception e){
                Log.e("Hoge",e.getMessage());
            }
        }
    }

    //非暗号領域読み取りコマンド（WAON番号領域特化）
    private byte[] readWithoutEncryption(byte[] idm, int blocksize) throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream(100); //とりあえず

        //readWithoutEncryptionコマンド組み立て
        bout.write(0); //コマンド長（後で入れる）
        bout.write(0x06); //0x06はRead Without Encryptionを表す
        bout.write(idm); //8byte:idm
        bout.write(1); //サービス数
        bout.write(0x0B); //サービスコードリスト
        bout.write(0x31); //サービスコードリスト
        bout.write(blocksize); //ブロック数

        for (int i = 0; i < blocksize; i++) {
            bout.write(0x80); //ブロックリスト
            bout.write(i);
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length;

        return msg;
    }

    //bytesを16進数型文字列に変換用関数
    private String bytesToHexString(byte[] bytes){
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);
        for(byte b: bytes){
            formatter.format("%02x",b);
        }
        //大文字にして戻す（見た目の調整だけ）
        return sb.toString().toUpperCase();
    }

}
