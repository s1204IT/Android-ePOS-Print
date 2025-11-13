package me.s1204.epson.epos.print;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {

    private EditText ip_addr;
    private EditText dev_id;
    private EditText xml;
    private Button send;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ip_addr = findViewById(R.id.ip_addr);
        dev_id = findViewById(R.id.dev_id);
        xml = findViewById(R.id.xml);
        send = findViewById(R.id.send);

        send.setOnClickListener(v -> {
            String ipAddress = ip_addr.getText().toString().trim();
            String printerId = dev_id.getText().toString().trim();
            String xmlContent = xml.getText().toString().trim();

            if (ipAddress.isEmpty() || printerId.isEmpty() || xmlContent.isEmpty()) {
                Toast.makeText(MainActivity.this, "IPアドレス、デバイスID、XMLコンテンツをすべて入力してください。", Toast.LENGTH_LONG).show();
                return;
            }

            final String address = "http://" + ipAddress + "/cgi-bin/epos/service.cgi?devid=" + printerId + "&timeout=10000";

            final String requestBody =
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                            "<s:Body>\n" +
                                xmlContent + "\n" +
                            "</s:Body>\n" +
                    "</s:Envelope>";

            new SendPrintTask().execute(address, requestBody);
        });
    }

    @SuppressLint("StaticFieldLeak")
    private class SendPrintTask extends AsyncTask<String, Void, String> {

        private Exception mException = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            send.setEnabled(false);
            Toast.makeText(MainActivity.this, "印刷データを送信中...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... params) {
            String address = params[0];
            String requestBody = params[1];
            String responseString = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(address);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                connection.setRequestProperty("SOAPAction", "\"\"");

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        responseString = response.toString();
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String errorLine;
                        while ((errorLine = br.readLine()) != null) {
                            errorResponse.append(errorLine.trim());
                        }
                        mException = new Exception("HTTPエラー: " + responseCode + " " + connection.getResponseMessage());
                    }
                }

            } catch (Exception e) {
                mException = e;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            send.setEnabled(true);

            if (mException != null) {
                Toast.makeText(MainActivity.this, "エラー: " + mException.getMessage(), Toast.LENGTH_LONG).show();
            } else if (result != null) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new InputSource(new StringReader(result)));

                    Element el = (Element) doc.getElementsByTagName("response").item(0);
                    String successAttribute = (el != null) ? el.getAttribute("success") : "success属性が見つかりません";

                    AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity.this);
                    dlg.setTitle("Epson ePOS-Print");
                    dlg.setMessage("プリンター応答: " + successAttribute);
                    dlg.setPositiveButton("OK", null);
                    dlg.show();

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "応答XMLのパースエラー: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "プリンターからの応答がありません。", Toast.LENGTH_LONG).show();
            }
        }
    }
}
