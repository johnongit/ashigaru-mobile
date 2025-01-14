package com.samourai.wallet.paynym.fragments;

import static android.view.View.GONE;
import static java.lang.String.format;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.samourai.wallet.R;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.paynym.WebUtil;
import com.samourai.wallet.paynym.paynymDetails.PayNymDetailsActivity;
import com.samourai.wallet.widgets.CircleImageView;
import com.samourai.wallet.widgets.ItemDividerDecorator;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;


public class PaynymListFragment extends Fragment {

    private static final String TAG = "PaynymListFragment";

    private RecyclerView list;

    public static PaynymListFragment newInstance() {
        return new PaynymListFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.paynym_account_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.paynym_accounts_rv);
        list.addItemDecoration(new ItemDividerDecorator(this.getResources().getDrawable(R.drawable.divider_grey)));
        list.setLayoutManager(new LinearLayoutManager(this.getContext()));
        list.setNestedScrollingEnabled(true);
        list.setAdapter(new PaynymAdapter());
    }

    public void addPcodes(ArrayList<String> list) {
        if(isAdded()){
            ((PaynymAdapter)this.list.getAdapter()).setPcodes(list);
        }
    }

    private ArrayList<String> filterArchived(ArrayList<String> list) {
        ArrayList<String> filtered = new ArrayList<>();

        for (String item : list) {
            if (!BIP47Meta.getInstance().getArchived(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public void onPayNymItemClick(final String pcode, final View avatar, final boolean registered) {
        final ActivityOptionsCompat options = ActivityOptionsCompat.
                makeSceneTransitionAnimation(getActivity(), avatar, "profile");


        startActivity(new Intent(
                getActivity(),
                PayNymDetailsActivity.class)
                        .putExtra("pcode", pcode)
                        .putExtra("unregistered", !registered),
                options.toBundle());
    }


    class PaynymAdapter extends RecyclerView.Adapter<PaynymAdapter.ViewHolder> {

        private ArrayList<String> pcodes = new ArrayList<>();

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.paynym_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {


            final CircleImageView avatar = holder.avatar;
            final View itemView = holder.itemView;
            final TextView paynymCode = holder.paynymCode;
            final TextView paynymLabel = holder.paynymLabel;

            final String strPaymentCode = pcodes.get(position);
            if (strPaymentCode == null) {
                paynymCode.setText("");
                paynymLabel.setText("");
                avatar.setImageResource(R.drawable.paynym);
                return;
            }

            setPayNymLabels(strPaymentCode, paynymCode, paynymLabel);
            setPayNymLogos(strPaymentCode, avatar, itemView);

        }

        @Override
        public int getItemCount() {
            return pcodes.size();
        }

        public void setPcodes(ArrayList<String> list) {
            pcodes.clear();
            pcodes.addAll(list);
            this.notifyDataSetChanged();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            CircleImageView avatar;
            TextView paynymCode;
            TextView paynymLabel;

            ViewHolder(View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.paynym_avatar);
                paynymCode = itemView.findViewById(R.id.paynym_code);
                paynymLabel = itemView.findViewById(R.id.paynym_label);
            }
        }
    }

    private static void setPayNymLabels(
            final String strPaymentCode,
            final TextView paynymCode,
            final TextView paynymLabel) {

        if (!StringUtils.equals(
                BIP47Meta.getInstance().getName(strPaymentCode),
                BIP47Meta.getInstance().getDisplayLabel(strPaymentCode))) {

            paynymCode.setText(BIP47Meta.getInstance().getName(strPaymentCode));
            paynymLabel.setText(BIP47Meta.getInstance().getDisplayLabel(strPaymentCode));
            paynymLabel.setVisibility(View.VISIBLE);
        } else {
            paynymCode.setText(BIP47Meta.getInstance().getName(strPaymentCode));
            paynymLabel.setText("");
            paynymLabel.setVisibility(GONE);
        }
    }

    private void setPayNymLogos(String strPaymentCode, CircleImageView avatar, View itemView) {
        try {
            Picasso.get().load(WebUtil.PAYNYM_API + strPaymentCode + "/avatar")
                    .into(avatar, createPicassoCallback(itemView, strPaymentCode, avatar));
        } catch (final Throwable t) {
            /**
             * This catch block is useful if ever the onSuccess/onError callback system
             * throws a runtime exception.
             * It indicates a problem to be fixed, so we log in error.
             * This has already been the case through the method LogUtil#error.
             */
            Log.e(TAG, format("Throwable with Picasso on /avatar %s : %s", strPaymentCode, t.getMessage()), t);
            avatar.setImageResource(R.drawable.paynym);
            itemView.setOnClickListener(view -> onPayNymItemClick(strPaymentCode, avatar, false));
        }
    }

    @NonNull
    private Callback createPicassoCallback(
            final View itemView,
            final String strPaymentCode,
            final CircleImageView avatar) {
        return new Callback() {
            @Override
            public void onSuccess() {
                itemView.setOnClickListener(view -> onPayNymItemClick(strPaymentCode, avatar, true));
            }

            @Override
            public void onError(Exception e) {

                try {
                    Picasso.get().load(WebUtil.PAYNYM_API + "preview/" + strPaymentCode)
                            .into(avatar, new Callback() {
                                @Override
                                public void onSuccess() {
                                    itemView.setOnClickListener(view -> onPayNymItemClick(strPaymentCode, avatar, false));
                                }

                                @Override
                                public void onError(final Exception e) {
                                    Log.e(TAG, "issue when loading avatar for " + strPaymentCode, e);
                                    avatar.setImageResource(R.drawable.paynym);
                                    itemView.setOnClickListener(view -> onPayNymItemClick(strPaymentCode, avatar, false));
                                }
                            });
                } catch (final Throwable t) {
                    /**
                     * This catch block is useful if ever the onSuccess/onError callback system
                     * throws a runtime exception.
                     * It indicates a problem to be fixed, so we log in error.
                     * This has already been the case through the method LogUtil#error.
                     */
                    Log.e(TAG, format("Throwable with Picasso on /preview %s : %s", strPaymentCode, t.getMessage()), t);
                    avatar.setImageResource(R.drawable.paynym);
                    itemView.setOnClickListener(view -> onPayNymItemClick(strPaymentCode, avatar, false));
                }
            }
        };
    }
}
