package com.samourai.wallet.paynym.addPaynym;

import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.common.base.Splitter;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiActivity;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.fragments.CameraFragmentBottomSheet;
import com.samourai.wallet.paynym.paynymDetails.PayNymDetailsActivity;
import com.samourai.wallet.util.func.FormatsUtil;

import org.bitcoinj.core.AddressFormatException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static com.samourai.wallet.bip47.BIP47Meta.strSamouraiDonationPCode;
import static com.samourai.wallet.util.activity.ActivityHelper.getFirstItemFromClipboard;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AddPaynymActivity extends SamouraiActivity {

    private SearchView mSearchView;
    private ViewSwitcher viewSwitcher;
    private RecyclerView searchPaynymList;
    private static final String TAG = "AddPaynymActivity";
    private String pcode;

    private static final int EDIT_PCODE = 2000;
    private static final int SCAN_PCODE = 2077;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_paynym);
        setSupportActionBar(findViewById(R.id.toolbar_addpaynym));
        getWindow().setStatusBarColor(getResources().getColor(R.color.grey_accent));
        viewSwitcher = findViewById(R.id.viewswitcher_addpaynym);
        searchPaynymList = findViewById(R.id.search_list_addpaynym);
        searchPaynymList.setLayoutManager(new LinearLayoutManager(this));
        searchPaynymList.setAdapter(new SearchAdapter());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.add_paynym_scan_qr).setOnClickListener(view -> {
            CameraFragmentBottomSheet cameraFragmentBottomSheet = new CameraFragmentBottomSheet();
            cameraFragmentBottomSheet.show(getSupportFragmentManager(),cameraFragmentBottomSheet.getTag());
            cameraFragmentBottomSheet.setQrCodeScanListener(code -> {
                cameraFragmentBottomSheet.dismissAllowingStateLoss();
                processScan(code);
            });
        });

        findViewById(R.id.add_paynym_paste).setOnClickListener(view -> {
            pastePcode();
        });

        findViewById(R.id.dev_fund_button).setOnClickListener(view -> {

            Intent intent = new Intent(this, PayNymDetailsActivity.class);
            intent.putExtra("pcode", strSamouraiDonationPCode);
            intent.putExtra("label", BIP47Meta.getInstance().getDisplayLabel(strSamouraiDonationPCode));
            startActivityForResult(intent, EDIT_PCODE);
        });
    }

    private void pastePcode() {
        final ClipData.Item item = getFirstItemFromClipboard(this);
        processScan(isNotBlank(item.getText()) ? item.getText().toString() : EMPTY);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_paynym_menu, menu);
        mSearchView = (SearchView) menu.findItem(R.id.action_search_addpaynym).getActionView();

        menu.findItem(R.id.action_search_addpaynym).setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                viewSwitcher.showNext();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                viewSwitcher.showNext();
                return true;
            }
        });

        setUpSearch();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpSearch() {

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }


    private void processScan(String data) {

        if (data.startsWith("bitcoin://") && data.length() > 10) {
            data = data.substring(10);
        }
        if (data.startsWith("bitcoin:") && data.length() > 8) {
            data = data.substring(8);
        }

        if (FormatsUtil.getInstance().isValidPaymentCode(data)) {

            try {
                if (data.equals(BIP47Util.getInstance(this).getPaymentCode().toString())) {
                    Toast.makeText(this, R.string.bip47_cannot_scan_own_pcode, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (AddressFormatException afe) {
                ;
            }

            Intent intent = new Intent(this, PayNymDetailsActivity.class);
            intent.putExtra("pcode", data);
            startActivityForResult(intent, EDIT_PCODE);

        } else if (data.contains("?") && (data.length() >= data.indexOf("?"))) {

            String meta = data.substring(data.indexOf("?") + 1);

            String _meta = null;
            try {
                Map<String, String> map = new HashMap<String, String>();
                if (meta != null && meta.length() > 0) {
                    _meta = URLDecoder.decode(meta, "UTF-8");
                    map = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(_meta);
                }

                Intent intent = new Intent(this, PayNymDetailsActivity.class);
                intent.putExtra("pcode", data.substring(0, data.indexOf("?")));
                intent.putExtra("label", map.containsKey("title") ? map.get("title").trim() : "");
                startActivityForResult(intent, EDIT_PCODE);
            } catch (UnsupportedEncodingException uee) {
                ;
            } catch (Exception e) {
                ;
            }

        } else {
            Toast.makeText(this, R.string.scan_error, Toast.LENGTH_SHORT).show();
        }


    }

    class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {


        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.paynym_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            return 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            public ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
