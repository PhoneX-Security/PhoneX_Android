package net.phonex.ui.inapp;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.phonex.R;
import net.phonex.rest.entities.auth.products.Product;
import net.phonex.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by miroc on 29.10.15.
 */
public class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ViewHolder> {
    private static final String TAG = "ProductsAdapter";
    private List<Product> products;
    private boolean loaded = false;
    private WeakReference<Context> weakContext;

    private ActionListener actionListener;
    public interface ActionListener{
        void onProductSelected(Product product);
        void onProductsSet(int size);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        @InjectView(R.id.title) TextView title;
        @InjectView(R.id.description) TextView description;
        @InjectView(R.id.price) TextView price;
        @InjectView(R.id.icon) ImageView icon;
        @InjectView(R.id.product_wrapper) ViewGroup wrapper;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.inject(this, itemView);
        }
    }

    public ProductsAdapter(List<Product> products) {
        this.products = products;
    }

    public ProductsAdapter(List<Product> products, Context context) {
        this.products = products;
        weakContext = new WeakReference<Context>(context);

    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void replace(List<Product> products) {
        if (products != null){
            Log.df(TAG, "replace; list size = %d", products.size());
            this.products = products;
            if (actionListener != null){
                actionListener.onProductsSet(products.size());
            }

            notifyDataSetChanged();
        }
    }

    @Override
    public ProductsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.vf(TAG, "onCreateViewHolder");
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.products_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Log.vf(TAG, "onBindViewHolder");
        Product product = products.get(position);
        holder.title.setText(product.getDisplayName());

        String description = getDescription(product);
        if (description != null){
            holder.description.setText(description);
            holder.description.setVisibility(View.VISIBLE);
        } else {
            holder.description.setVisibility(View.GONE);
        }

        if (product.getSkuDetails() != null){
            holder.price.setVisibility(View.VISIBLE);
            holder.price.setText(product.getSkuDetails().getPrice());
        } else {
            // TODO make invisible
            holder.price.setVisibility(View.VISIBLE);
            holder.price.setText("-");
        }

        holder.wrapper.setOnClickListener(v -> {
            if (actionListener != null){
                actionListener.onProductSelected(product);
            }
        });
    }

    private String getDescription(Product product){
        Context context = weakContext.get();
        if (context != null){
           return context.getString(product.getTypeDescriptionResId());
        } else {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return products.size();
    }
}