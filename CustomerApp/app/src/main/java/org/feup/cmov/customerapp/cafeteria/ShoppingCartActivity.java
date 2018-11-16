package org.feup.cmov.customerapp.cafeteria;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.feup.cmov.customerapp.MainActivity;
import org.feup.cmov.customerapp.R;
import org.feup.cmov.customerapp.dataStructures.Product;
import org.feup.cmov.customerapp.dataStructures.Voucher;
import org.feup.cmov.customerapp.database.GetProducts;
import org.feup.cmov.customerapp.database.LocalDatabase;
import org.feup.cmov.customerapp.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ShoppingCartActivity extends AppCompatActivity {

    // request vouchers from SelectVoucherActivity
    private static final int REQUEST_VOUCHERS = 0;

    // API to get shows from server
    public GetProducts productsAPI;

    // tickets to validate
    ArrayList<Product> products;

    // adapter to products' list
    public ArrayAdapter<Product> productsAdapter;

    // selected vouchers
    ArrayList<Voucher> selectedVouchers = new ArrayList<>();

    // text view displaying total price
    TextView totalPrice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_cart);

        productsAPI = new GetProducts(this);
        Thread thr = new Thread(productsAPI);
        thr.start();

        Button selectVouchers = findViewById(R.id.btn_select_vouchers);
        selectVouchers.setOnClickListener((View v)->selectVouchers());

        Button buy = findViewById(R.id.btn_buy_SC);
        buy.setOnClickListener((View v)->buyOrder());

        totalPrice = findViewById(R.id.total_price_SC);
    }

    public void handleResponse(int code, String response, List<Product> prods) {
        ArrayList<Product> productsList = new ArrayList<>(prods);
        if (code == Constants.OK_RESPONSE) {
            products = restoreSelectedProducts(productsList);

            ListView list_products = findViewById(R.id.list_shopping_cart);
            productsAdapter = new ShoppingCartAdapter(this, products);
            list_products.setAdapter(productsAdapter);

            // set price text
            setPriceText();
        } else {
            // show error response
            showToast(response);
        }
    }

    /**
     * Shows toast message
     * @param toast - message to show
     */
    public void showToast(final String toast)
    {
        runOnUiThread(() -> Toast.makeText(ShoppingCartActivity.this, toast, Toast.LENGTH_LONG).show());
    }

    /**
     * Sets price text on the layout (runs on the UI's thread)
     */
    public void setPriceText() {
        runOnUiThread(() -> {
            String price = "TOTAL PRICE (without vouchers): " + getTotalPrice() + " €";
            totalPrice.setText(price);
        });
    }

    /**
     * Returns shopping cart's products
     * @param products - all products that we got from server
     * @return list of products to be bought by the user
     */
    public ArrayList<Product> restoreSelectedProducts(ArrayList<Product> products) {
        ArrayList<Product> productList = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREF, Context.MODE_PRIVATE);
        if (prefs.contains(Constants.PREF_PRODUCTS)) {
            // get set from shared preferences
            Set<String> productSet = prefs.getStringSet(Constants.PREF_PRODUCTS, null);

            // get array stored in shared preferences
            ArrayList<Product> prefsProducts = new ArrayList<>();

            for(String p : productSet) {
                if(p.contains(" ")){
                    // id is first part of string before first space
                    String id = p.substring(0, p.indexOf(" "));

                    // quantity is second part of string after first space
                    String quantity = p.substring(p.indexOf(" ")+1, p.length());

                    Product product = new Product(Integer.parseInt(id), Integer.parseInt(quantity));
                    prefsProducts.add(product);
                }
            }

            // goes through all products stored in shared preferences (id, quantity) and populates array with all needed data
            for (Product sp : prefsProducts) {
                for (Product p : products) {
                    if (p.getId() == sp.getId()) {
                        p.setQuantity(sp.getQuantity());        // sets product's order quantity
                        productList.add(p);
                    }
                }
            }
        }

        return productList;
    }

    /**
     * Calculates total price of all the shopping cart's products
     * @return total price rounded up to two digits
     */
    private String getTotalPrice() {
        double price = 0.0;

        for(Product p : products) {
            price += p.getTotalPrice();
        }

        double rounded = (double) Math.round(price * 100.0) / 100.0;
        return Double.toString(rounded);
    }

    /**
     * Start SelectVoucherActivity so the user can select vouchers
     */
    private void selectVouchers() {
        Intent intent = new Intent(getApplicationContext(), SelectVoucherActivity.class);

        if (selectedVouchers.size() > 0) {
            Bundle argument = new Bundle();
            argument.putSerializable(Constants.SELECTED_VOUCHERS, selectedVouchers);
            intent.putExtras(argument);
        }

        startActivityForResult(intent, REQUEST_VOUCHERS);
    }

    /**
     *
     * @param requestCode - code of the request
     * @param resultCode - result given by the callback activity
     * @param data - data returned by the callback activity
     */
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_VOUCHERS) {
            selectedVouchers = (ArrayList<Voucher>) data.getSerializableExtra(Constants.REQUESTED_VOUCHERS);

            TextView vouchersTV = findViewById(R.id.text_vouchers_selected);

            String vouchers_selected = selectedVouchers.size() + " vouchers selected";
            vouchersTV.setText(vouchers_selected);
        }
    }

    /**
     * Called when buy button is clicked
     */
    private void buyOrder() {
        CafeteriaActivity.resetSharedPrefs(this);
        deleteVouchersDatabase();

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);

        showToast(Constants.ORDER_IN_PROGRESS);

        // TODO: send order to validation terminal!!!!!!!!!!
    }

    public void deleteVouchersDatabase() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LocalDatabase db = LocalDatabase.getInstance(getApplicationContext());

                if (LocalDatabase.checkDataBase(getApplicationContext())) {
                    for(Voucher v : selectedVouchers) {
                        db.deleteVoucher(getApplicationContext(), v);
                    }
                }
            }
        });
    }

}
