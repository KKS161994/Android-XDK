package com.layer.xdk.ui.message.image;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import com.layer.xdk.ui.R;
import com.layer.xdk.ui.message.MessagePartUtils;
import com.layer.xdk.ui.message.model.Action;
import com.layer.xdk.ui.message.model.MessageModel;
import com.layer.xdk.ui.util.imagecache.ImageCacheWrapper;
import com.layer.xdk.ui.util.imagecache.ImageRequestParameters;
import com.layer.xdk.ui.util.imagecache.PicassoImageCacheWrapper;
import com.layer.xdk.ui.util.imagecache.requesthandlers.MessagePartRequestHandler;
import com.layer.xdk.ui.util.json.AndroidFieldNamingStrategy;
import com.squareup.picasso.Picasso;

import java.io.InputStreamReader;

public class ImageMessageModel extends MessageModel {

    public static final String ACTION_EVENT_OPEN_URL = "open-url";

    public static final String ROOT_MIME_TYPE = "application/vnd.layer.image+json";
    private static final String ROLE_SOURCE = "source";
    private static final String ROLE_PREVIEW = "preview";

    private static ImageCacheWrapper sImageCacheWrapper;

    private static final int PLACEHOLDER = R.drawable.xdk_ui_message_item_cell_placeholder;

    private final Gson mGson;
    private ImageMessageMetadata mMetadata;

    private ImageRequestParameters mPreviewRequestParameters;
    private ImageRequestParameters mSourceRequestParameters;

    public ImageMessageModel(Context context, LayerClient layerClient, Message message) {
        super(context, layerClient, message);
        mGson = new GsonBuilder().setFieldNamingStrategy(new AndroidFieldNamingStrategy()).create();
    }

    public Class<ImageMessageView> getRendererType() {
        return ImageMessageView.class;
    }

    @Override
    public int getViewLayoutId() {
        return 0;
    }

    @Override
    public int getContainerViewLayoutId() {
        return R.layout.xdk_ui_standard_message_container;
    }

    @Override
    protected void parse(@NonNull MessagePart messagePart) {
        parseRootMessagePart(messagePart);
    }

    @Override
    protected void parseChildPart(@NonNull MessagePart childMessagePart) {
        if (MessagePartUtils.isRole(childMessagePart, ROLE_PREVIEW)) {
            parsePreviewPart(childMessagePart);
        } else if (MessagePartUtils.isRole(childMessagePart, ROLE_SOURCE)) {
            parseSourcePart(childMessagePart);
        }
    }

    @Override
    protected boolean shouldDownloadContentIfNotReady(MessagePart messagePart) {
        if (MessagePartUtils.isRoleRoot(messagePart)) {
            return true;
        } else if (MessagePartUtils.isRole(messagePart, ROLE_PREVIEW)) {
            return true;
        } else if (MessagePartUtils.isRole(messagePart, ROLE_SOURCE)) {
            return true;
        }

        return false;
    }

    @Override
    public String getActionEvent() {
        if (super.getActionEvent() != null) {
            return super.getActionEvent();
        }

        if (mMetadata.getAction() != null) {
            return mMetadata.getAction().getEvent();
        } else {
            return ACTION_EVENT_OPEN_URL;
        }
    }

    @Override
    public JsonObject getActionData() {
        if (super.getActionData().size() > 0) {
            return super.getActionData();
        }

        if (mMetadata.getAction() != null) {
            return mMetadata.getAction().getData();
        } else {
            Action action = new Action(ACTION_EVENT_OPEN_URL);
            String url;
            int width, height;
            if (mMetadata.getPreviewUrl() != null) {
                url = mMetadata.getPreviewUrl();
                width = mMetadata.getPreviewWidth();
                height = mMetadata.getPreviewHeight();
            } else if (mMetadata.getSourceUrl() != null) {
                url = mMetadata.getSourceUrl();
                width = mMetadata.getWidth();
                height = mMetadata.getHeight();
            } else {
                if (mSourceRequestParameters != null && mSourceRequestParameters.getUri() != null) {
                    url = mSourceRequestParameters.getUri().toString();
                } else {
                    url = mPreviewRequestParameters.getUri().toString();
                }
                width = mMetadata.getWidth();
                height = mMetadata.getHeight();
            }

            action.getData().addProperty("url", url);
            action.getData().addProperty("mime-type", mMetadata.getMimeType());
            action.getData().addProperty("width", width);
            action.getData().addProperty("height", height);
            action.getData().addProperty("orientation", mMetadata.getOrientation());
            return action.getData();
        }
    }

    @Nullable
    @Override
    public String getPreviewText() {
        String title = getTitle();
        return title != null ? title : getContext().getString(R.string.xdk_ui_image_message_preview_text);
    }

    /*
    * Private methods
    */

    private void parseRootMessagePart(MessagePart messagePart) {
        JsonReader reader = new JsonReader(new InputStreamReader(messagePart.getDataStream()));
        mMetadata = mGson.fromJson(reader, ImageMessageMetadata.class);

        Message message = getMessage();
        if (!MessagePartUtils.hasMessagePartWithRole(message, ROLE_PREVIEW, ROLE_SOURCE)) {
            ImageRequestParameters.Builder previewRequestBuilder = new ImageRequestParameters.Builder();
            ImageRequestParameters.Builder sourceRequestBuilder = new ImageRequestParameters.Builder();
            String previewUrl;
            int width = 0;
            int height = 0;
            if (mMetadata.getPreviewUrl() != null) {
                previewUrl = mMetadata.getPreviewUrl();
                width = mMetadata.getPreviewWidth();
                height = mMetadata.getPreviewHeight();
            } else if (mMetadata.getSourceUrl() != null) {
                previewUrl = mMetadata.getSourceUrl();
                width = mMetadata.getWidth();
                height = mMetadata.getHeight();

                sourceRequestBuilder.url(mMetadata.getSourceUrl());
                if (width > 0 && height > 0) {
                    sourceRequestBuilder.resize(width, height);
                }
                sourceRequestBuilder.exifOrientation(mMetadata.getOrientation())
                        .tag(getClass().getSimpleName());

                mSourceRequestParameters = sourceRequestBuilder.build();
            } else {
                previewUrl = null;
            }

            if (width > 0 && height > 0) {
                previewRequestBuilder.resize(width, height);
            }

            previewRequestBuilder.url(previewUrl)
                    .placeHolder(PLACEHOLDER)
                    .exifOrientation(mMetadata.getOrientation())
                    .tag(getClass().getSimpleName());

            mPreviewRequestParameters = previewRequestBuilder.build();
        }
    }

    private void parsePreviewPart(MessagePart messagePart) {
        ImageRequestParameters.Builder builder = new ImageRequestParameters.Builder();
        if (messagePart.getId() != null) {
            builder.uri(messagePart.getId());
        } else {
            builder.url(mMetadata.getPreviewUrl());
        }

        builder.placeHolder(PLACEHOLDER)
                .resize(mMetadata.getPreviewWidth(), mMetadata.getPreviewHeight())
                .exifOrientation(mMetadata.getOrientation())
                .tag(getClass().getSimpleName());

        mPreviewRequestParameters = builder.build();
    }

    private void parseSourcePart(MessagePart messagePart) {
        ImageRequestParameters.Builder builder = new ImageRequestParameters.Builder();
        if (messagePart.getId() != null) {
            builder.uri(messagePart.getId());
        } else {
            builder.url(mMetadata.getSourceUrl());
        }

        builder.placeHolder(PLACEHOLDER)
                .resize(mMetadata.getWidth(), mMetadata.getHeight())
                .exifOrientation(mMetadata.getOrientation())
                .tag(getClass().getSimpleName());

        mSourceRequestParameters = builder.build();
    }

    @Nullable
    public ImageMessageMetadata getMetadata() {
        return mMetadata;
    }

    /*
    * Setters, getters, bindings
    */

    public ImageCacheWrapper getImageCacheWrapper() {
        if (sImageCacheWrapper == null) {
            sImageCacheWrapper = new PicassoImageCacheWrapper(new Picasso.Builder(getContext())
                    .addRequestHandler(new MessagePartRequestHandler(getLayerClient()))
                    .build());
        }
        return sImageCacheWrapper;
    }

    public static void setImageCacheWrapper(ImageCacheWrapper imageCacheWrapper) {
        sImageCacheWrapper = imageCacheWrapper;
    }

    public ImageRequestParameters getPreviewRequestParameters() {
        return mPreviewRequestParameters;
    }

    public ImageRequestParameters getSourceRequestParameters() {
        return mSourceRequestParameters;
    }

    @Override
    public String getTitle() {
        return mMetadata != null ? mMetadata.getTitle() : null;
    }

    @Override
    public String getDescription() {
        return mMetadata != null ? mMetadata.getSubtitle() : null;
    }

    @Override
    public String getFooter() {
        return mMetadata != null ? mMetadata.getSubtitle() : null;
    }

    @Override
    @ColorRes
    public int getBackgroundColor() {
        return R.color.transparent;
    }

    @Override
    public boolean getHasContent() {
        return mMetadata != null && (mPreviewRequestParameters != null || mSourceRequestParameters != null);
    }

}
