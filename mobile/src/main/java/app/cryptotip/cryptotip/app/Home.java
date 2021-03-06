package app.cryptotip.cryptotip.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gani.lib.http.GRestCallback;
import com.gani.lib.http.GRestResponse;
import com.gani.lib.http.HttpAsyncGet;
import com.gani.lib.http.HttpHook;
import com.gani.lib.logging.GLog;
import com.gani.lib.ui.ProgressIndicator;
import com.gani.lib.ui.view.GTextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import org.json.JSONException;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.exceptions.MessageDecodingException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import app.cryptotip.cryptotip.app.database.DbMap;
import app.cryptotip.cryptotip.app.http.MyImmutableParams;
import app.cryptotip.cryptotip.app.json.MyJsonObject;
import app.cryptotip.cryptotip.app.transaction.TransactionListActivity;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class Home extends AppCompatActivity {
    private String pubKey;
    private String walletFilePath;
    //    private String fiatCurrency;
//    private String cryptoCurrency;
    private String cryptoBalance;
    private String selectedFiatCurrency;
    private String selectedCryptoCurrency;
    private ImageView pubKeyimageView;
    private TextView pubKeyTview;
    private GTextView fiatBalanceTextView;
    private GTextView cryptoBalanceTextView;
    private Drawer activityDrawer;
    private Drawer settingsDrawer;
    protected DrawerLayout mDrawerLayout;
    private static final int CURRENCY_CHANGE = 555;
    public static final String WALLET_FILE_PATH = "walletFilePath";
    public static final String FIAT_PRICE = "fiatPrice";
    public static final String SELECTED_FIAT_CURRENCY = "selectedFiatCurrency";
    public static final String SELECTED_CRYPTO_CURRENCY = "selectedCryptoCurrency";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        cryptoBalance = "0";

        walletFilePath = DbMap.get(WALLET_FILE_PATH);
        if (walletFilePath == null) {
            walletFilePath = getFilesDir().getPath().concat("/" + createWallet());

            DbMap.put(WALLET_FILE_PATH, walletFilePath);
        }

        setUpToolbarAndDrawers();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CURRENCY_CHANGE) {
            this.onPostCreate(null);
        }
    }


    @Override
    public void onPostCreate(@Nullable Bundle savedinstaceState) {
        super.onPostCreate(savedinstaceState);
        pubKeyTview = findViewById(R.id.public_key_text_view);
        pubKeyTview.setTextIsSelectable(true);
        pubKeyimageView = findViewById(R.id.public_key_qr_code);
        cryptoBalanceTextView = findViewById(R.id.eth_balance_text_view);
        fiatBalanceTextView = findViewById(R.id.fiat_balance_text_view);

        pubKeyTview.setText(getStringFromFile(walletFilePath));
        try {
            Credentials creds = WalletUtils.loadCredentials("atestpasswordhere", walletFilePath);
            pubKeyTview.setText(creds.getAddress());
            pubKey = creds.getAddress();
        } catch (CipherException e) {
            Log.e("fail", "exception " + e);
        } catch (IOException e) {
            Log.e("fail", "exception " + e);
        }

        if (pubKey != null) {
            try {
                Bitmap bmp = encodeAsBitmap(pubKey);
                pubKeyimageView.setImageBitmap(bmp);
            } catch (WriterException e) {
                Log.e("fail", "exception " + e);
            }
        }

        selectedFiatCurrency = DbMap.get(SELECTED_FIAT_CURRENCY);
        if (selectedFiatCurrency == null) {
            selectedFiatCurrency = "USD";
            DbMap.put(SELECTED_FIAT_CURRENCY, selectedFiatCurrency);
        }

        selectedCryptoCurrency = DbMap.get(SELECTED_CRYPTO_CURRENCY);
        if (selectedCryptoCurrency == null) {
            selectedCryptoCurrency = "ETH";
            DbMap.put(SELECTED_CRYPTO_CURRENCY, selectedCryptoCurrency);
        }

        refresh();

        CardView refreshButton = findViewById(R.id.refresh_button_card_view);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        CardView ledgerButton = findViewById(R.id.ledger_button_card_view);
        ledgerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //access ledger
            }
        });
    }

    private void refresh() {

        Web3j weby = Web3jFactory.build(new HttpService("https://rinkeby.infura.io/tQmR2iidoG7pjW1hCcCf"));
        EthGetBalance ethGBalance = null;
        try {
            ethGBalance = weby.ethGetBalance(pubKey, DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (InterruptedException e) {

        } catch (ExecutionException e) {

        }

        if (pubKey != null) {
            try {
                Bitmap bmp = encodeAsBitmap(pubKey);
                pubKeyimageView.setImageBitmap(bmp);
            } catch (WriterException e) {
                Log.e("fail", "exception " + e);
            }

            pubKeyTview.setText(pubKey);
        }

        cryptoBalanceTextView.setTextIsSelectable(true);
        if (selectedCryptoCurrency.contentEquals("ETH")) {
            try {
                if (ethGBalance != null) {
                    BigInteger bigIntBal = ethGBalance.getBalance();
                    cryptoBalance = new BalanceHelper().convertWeiToEth(bigIntBal);
                    cryptoBalanceTextView.setText(selectedCryptoCurrency.concat(" Balance : " + cryptoBalance));
                } else {
                    cryptoBalanceTextView.setText("failed to retrieve balance");
                }
            } catch (MessageDecodingException e) {
                GLog.e(getClass(), "Error getting eth balance " + e);
            }
        } else {
            String contractAddress = getContractAddress(selectedCryptoCurrency);
            if (contractAddress != null) {
                try {

                    Function function = new Function(
                            "balanceOf", Arrays.<Type>asList(new Address(pubKey)), new ArrayList<TypeReference<?>>()); //Uint256
                    String encodedFunction = FunctionEncoder.encode(function);
                    org.web3j.protocol.core.methods.response.EthCall response = weby.ethCall(
                            Transaction.createEthCallTransaction(pubKey, contractAddress, encodedFunction), DefaultBlockParameterName.LATEST)
                            .sendAsync().get();

                    Address result = new Address(response.getResult());

                    cryptoBalance = new BalanceHelper().convertWeiToEth(result.toUint160().getValue());
                    cryptoBalanceTextView.setText(selectedCryptoCurrency.concat(" Balance : " + cryptoBalance));

                } catch (InterruptedException e) {
                    cryptoBalanceTextView.setText("failed to retrieve balance");
                    GLog.e(getClass(), "interupted " + e);
                } catch (ExecutionException e) {
                    cryptoBalanceTextView.setText("failed to retrieve balance");
                    GLog.e(getClass(), "execution excptional " + e);
                }
            } else {
                cryptoBalanceTextView.setText("failed to retrieve balance");
            }
        }


        //todo support erc20 prices
        new HttpAsyncGet("https://min-api.cryptocompare.com/data/price?fsym=ETH&tsyms=" + selectedFiatCurrency, MyImmutableParams.EMPTY, HttpHook.DUMMY, new GRestCallback(this, new ProgressIndicator() {
            @Override
            public void showProgress() {
                // TODO: 11/06/18
            }

            @Override
            public void hideProgress() {
                // TODO: 11/06/18
            }
        }) {
            @Override
            protected void onRestResponse(GRestResponse r) throws JSONException {
                super.onRestResponse(r);
                MyJsonObject object = new MyJsonObject(r.getJsonString());
                String price = object.getString(selectedFiatCurrency);
                DbMap.put(FIAT_PRICE, price);
                String text = selectedFiatCurrency + " : " + price + "\nvalue : " + String.valueOf(Double.valueOf(cryptoBalance) * Double.valueOf(price));
                fiatBalanceTextView.setText(text);
            }
        }).execute();
    }

    private String createWallet() {
        try {
            return WalletUtils.generateNewWalletFile("atestpasswordhere", getFilesDir(), false);
        } catch (IOException e) {
            Log.e(getClass().getName(), "exception " + e);
        } catch (InvalidAlgorithmParameterException e) {
            Log.e(getClass().getName(), "exception " + e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(getClass().getName(), "exception " + e);
        } catch (NoSuchProviderException e) {
            Log.e(getClass().getName(), "exception " + e);
        } catch (CipherException e) {
            Log.e(getClass().getName(), "exception " + e);
        }
        return null;
    }

    Bitmap encodeAsBitmap(String str) throws WriterException {

        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, 600, 600, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, 600, 0, 0, w, h);
        return bitmap;
    }

    public static String getStringFromFile(String filePath) {
        File fl = new File(filePath);
        try {
            FileInputStream fin = new FileInputStream(fl);
            String ret = convertStreamToString(fin);
            //Make sure you close all streams.
            fin.close();
            return ret;
        } catch (IOException e) {
            Log.e("fail", "exception " + e);
        }
        return null;
    }

    public static String convertStreamToString(InputStream is) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            Log.e("fail", "exception " + e);
        }
        return null;
    }

    public static String getContractAddress(String tokenID) {
        switch (tokenID) {
            case "ALPH":
                return "0xfb0fBFd118D25bBDB82fF6bFe9b08f3Ae9B68a64";
            case "BETA":
                return "0xadADEef132DbE73cF951DD77C4cFcF87D682F543";
            case "OMEG":
                return "0x9f2B11C377a6bBA0D4dFaC81d7A4768955AC5900";
            default:
                return null;
        }
    }

    private BigInteger generateBigInt() {
        String hex = "0x1000000000000000000000000000000000";
        Log.e(getClass().getName(), "length == " + hex.length());
        BigInteger bigInteger = new BigInteger("20000000");// uper limit
        BigInteger min = new BigInteger("10000");// lower limit
        BigInteger bigInteger1 = bigInteger.subtract(min);
        Random rnd = new Random();
        int maxNumBitLength = bigInteger.bitLength();

        BigInteger aRandomBigInt;

        Log.e(getClass().getName(), "midlle way here");

        aRandomBigInt = new BigInteger(maxNumBitLength, rnd);
        if (aRandomBigInt.compareTo(min) < 0)
            aRandomBigInt = aRandomBigInt.add(min);
        if (aRandomBigInt.compareTo(bigInteger) >= 0)
            aRandomBigInt = aRandomBigInt.mod(bigInteger1).add(min);

        Log.e(getClass().getName(), "return a value ");

        return aRandomBigInt;
    }


    private android.app.AlertDialog createFiatCurrencySelectorDialog() {
        final CharSequence[] fiatCurrencyCodes = {"AUD", "USD", "EUR", "GBP", "INR", "MYR", "IDR", "RUB", "NZD", "THB", "SGD", "JPY", "CNY", "CAD", "KRW", "SAR", "AED"};

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Currecny")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DbMap.put(SELECTED_FIAT_CURRENCY, selectedFiatCurrency);
                        refresh();
                    }
                })
                .setSingleChoiceItems(fiatCurrencyCodes, fiatCurrencyCodes.length, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedFiatCurrency = fiatCurrencyCodes[which].toString();
                    }
                }).create();
        return dialog;
    }

    private android.app.AlertDialog createCryptoCurrencySelectorDialog() {
        final CharSequence[] cryptoCurrencyCodes = {"ETH", "OMEG", "BETA", "ALPH"};

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Currecny")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DbMap.put(SELECTED_CRYPTO_CURRENCY, selectedCryptoCurrency);
                        refresh();
                    }
                })
                .setSingleChoiceItems(cryptoCurrencyCodes, cryptoCurrencyCodes.length, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedCryptoCurrency = cryptoCurrencyCodes[which].toString();
                    }
                }).create();
        return dialog;
    }

    @Override
    public void onBackPressed() {
        //handle the back press :D close the drawer first and if the drawer is closed close the activity
        if (activityDrawer != null && activityDrawer.isDrawerOpen()) {
            activityDrawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    private void setUpToolbarAndDrawers() {
        Toolbar tbar = this.findViewById(R.id.toolbar);
        tbar.setTitle("cryptoTIP");
        setSupportActionBar(tbar);
        ImageView iconView = new ImageView(this);
        iconView.setPadding(750, 0, 0, 0); //todo icon will be off edge of screen on lower resolution screen
        iconView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_settings_20px, null));
        tbar.addView(iconView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 100));

        activityDrawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(tbar)
                .withDisplayBelowStatusBar(true)
                .withTranslucentStatusBar(false)
                .addDrawerItems(
                        new PrimaryDrawerItem().withIdentifier(45).withName("Send"),
                        new PrimaryDrawerItem().withIdentifier(77).withName("Tip History"))
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        switch (position) {
                            case 0:
                                startActivity(new ReceiverAddressActivity().intent(Home.this));
                                break;
                            case 1:
                                startActivity(new TransactionListActivity().intent(Home.this));
                                break;
                        }
                        return false;
                    }
                })
                .build();

        settingsDrawer = new DrawerBuilder()
                .withActivity(this)
                .withDisplayBelowStatusBar(true)
                .withTranslucentStatusBar(false)
                .addDrawerItems(
                        new PrimaryDrawerItem().withIdentifier(45).withName("Fiat Currency"),
                        new PrimaryDrawerItem().withIdentifier(77).withName("Crypto Currency"))
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        switch (position) {
                            case 0:
                                createFiatCurrencySelectorDialog().show();
                                break;
                            case 1:
                                createCryptoCurrencySelectorDialog().show();
                                break;
                        }
                        return false;
                    }
                })
                .withDrawerGravity(Gravity.END)
                .append(activityDrawer);
        iconView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsDrawer.openDrawer();
            }
        });
    }
}