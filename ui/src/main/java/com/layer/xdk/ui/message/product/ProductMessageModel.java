package com.layer.xdk.ui.message.product;

import android.content.Context;
import android.databinding.Bindable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.MessagePart;
import com.layer.xdk.ui.R;
import com.layer.xdk.ui.message.choice.ChoiceMessageModel;
import com.layer.xdk.ui.message.choice.ChoiceMetadata;
import com.layer.xdk.ui.message.model.MessageModel;
import com.layer.xdk.ui.message.view.MessageView;
import com.layer.xdk.ui.util.imagecache.ImageCacheWrapper;
import com.layer.xdk.ui.util.imagecache.ImageRequestParameters;
import com.layer.xdk.ui.util.imagecache.PicassoImageCacheWrapper;
import com.layer.xdk.ui.util.imagecache.requesthandlers.MessagePartRequestHandler;
import com.layer.xdk.ui.util.json.AndroidFieldNamingStrategy;
import com.squareup.picasso.Picasso;

import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ProductMessageModel extends MessageModel {
    public static final String MIME_TYPE = "application/vnd.layer.product+json";
    private static final String DEFAULT_ACTION_EVENT = "open-url";
    private static final String DEFAULT_ACTION_DATA_KEY = "url";

    private List<ChoiceMessageModel> mOptions;
    private static ImageCacheWrapper sImageCacheWrapper;

    private Gson mGson;
    private ProductMessageMetadata mMetadata;

    public ProductMessageModel(Context context, LayerClient layerClient) {
        super(context, layerClient);
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingStrategy(new AndroidFieldNamingStrategy());
        mGson = gsonBuilder.create();
        mOptions = new ArrayList<>();
    }

    @Override
    public Class<? extends MessageView> getRendererType() {
        return ProductMessageView.class;
    }

    @Override
    protected void parse(@NonNull MessagePart messagePart) {
        JsonReader reader;
        if (messagePart == getRootMessagePart()) {
            reader = new JsonReader(new InputStreamReader(messagePart.getDataStream()));
            mMetadata = mGson.fromJson(reader, ProductMessageMetadata.class);
            mOptions.clear();
        }
    }

    @Override
    protected boolean shouldDownloadContentIfNotReady(@NonNull MessagePart messagePart) {
        return true;
    }

    @Nullable
    @Override
    public String getTitle() {
        return null;
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @Nullable
    @Override
    public String getFooter() {
        return null;
    }

    @Override
    public boolean getHasContent() {
        return mMetadata != null;
    }

    @Nullable
    @Override
    public String getActionEvent() {
        String actionEvent = super.getActionEvent();
        if (actionEvent == null && mMetadata != null) {
            if (mMetadata.getAction() != null) {
                actionEvent = mMetadata.getAction().getEvent();
            } else if (mMetadata.getUrl() != null) {
                actionEvent = DEFAULT_ACTION_EVENT;
            }
        }

        return actionEvent;
    }

    @NonNull
    @Override
    public JsonObject getActionData() {
        JsonObject data = super.getActionData();
        if (data.size() == 0 && mMetadata != null) {
            if (mMetadata.getAction() != null) {
                data = mMetadata.getAction().getData();
            } else if (mMetadata.getUrl() != null) {
                data.addProperty(DEFAULT_ACTION_DATA_KEY, mMetadata.getUrl());
            }
        }

        return data;
    }

    @Nullable
    @Bindable
    public String getBrand() {
        return mMetadata != null ? mMetadata.getBrand() : null;
    }

    @Nullable
    @Bindable
    public String getName() {
        return mMetadata != null ? mMetadata.getName() : null;
    }

    @Nullable
    @Bindable
    public String getProductDescription() {
        return mMetadata != null ? mMetadata.getDescription() : null;
    }

    @Nullable
    @Override
    public String getPreviewText() {
        String name = getName();
        return name != null ? name : getContext().getString(R.string.ui_product_message_preview_text);
    }

    @Bindable
    @Nullable
    public String getPrice() {
        if (mMetadata != null && mMetadata.getPrice() != null) {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
            currencyFormat.setCurrency(Currency.getInstance(mMetadata.getCurrency(getContext())));
            return currencyFormat.format(mMetadata.getPrice());
        }

        return null;
    }

    @Bindable
    public ImageCacheWrapper getImageCacheWrapper() {
        if (sImageCacheWrapper == null) {
            sImageCacheWrapper = new PicassoImageCacheWrapper(new Picasso.Builder(getContext())
                    .addRequestHandler(new MessagePartRequestHandler(getLayerClient()))
                    .build());
        }
        return sImageCacheWrapper;
    }

    @Bindable
    @Nullable
    public ImageRequestParameters getImageRequestParameters() {
        if (mMetadata != null) {
            ImageRequestParameters.Builder builder = new ImageRequestParameters.Builder();
            String url = mMetadata.getImageUrls() != null && mMetadata.getImageUrls().size() > 0 ?
                    mMetadata.getImageUrls().get(0) : null;

            if (url != null) {
                builder.url(url);
            } else {
                return null;
            }

            builder.centerCrop(true)
                    .resize(getContext().getResources().getDimensionPixelSize(R.dimen.ui_product_message_image_width),
                            getContext().getResources().getDimensionPixelSize(R.dimen.ui_product_message_image_height))
                    .tag(getClass().getSimpleName());

            return builder.build();
        }

        return null;
    }

    @Nullable
    public ProductMessageMetadata getMetadata() {
        return mMetadata;
    }

    @NonNull
    @Bindable
    public List<ChoiceMessageModel> getOptions() {
        if (mOptions.isEmpty()) {
            List<MessageModel> childMessageModels = getChildMessageModels();
            if (childMessageModels != null) {
                for (MessageModel model : childMessageModels) {
                    if (model instanceof ChoiceMessageModel) {
                        mOptions.add((ChoiceMessageModel) model);
                    }
                }
            }
        }

        return mOptions;
    }

    @Nullable
    public String getSelectedOptionsAsCommaSeparatedList() {
        List<String> productTexts = new ArrayList<>();
        List<ChoiceMessageModel> options = getOptions();
        if (options != null && !options.isEmpty()) {
            for (ChoiceMessageModel option : options) {
                Iterator<String> iterator = option.getSelectedChoices() != null ? option.getSelectedChoices().iterator() : null;
                // Use just the first choice for now, the remaining will be displayed in an
                // expanded product message view, to be built later.
                String choiceId = iterator != null && iterator.hasNext() ? iterator.next() : null;
                List<ChoiceMetadata> choices = option.getChoiceMessageMetadata() != null ? option.getChoiceMessageMetadata().getChoices() : null;

                if (choices != null && choices.size() > 0) {
                    for (ChoiceMetadata choice : choices) {
                        if (choice.getId().equals(choiceId)) {
                            productTexts.add(choice.getText());

                            break;
                        }
                    }
                }
            }
        }

        return !productTexts.isEmpty() ? TextUtils.join(", ", productTexts) : null;
    }
}
