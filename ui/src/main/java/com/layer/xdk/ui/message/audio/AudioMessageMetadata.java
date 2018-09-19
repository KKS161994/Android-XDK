package com.layer.xdk.ui.message.audio;

import android.support.annotation.Dimension;

import com.google.gson.annotations.SerializedName;
import com.layer.xdk.ui.message.MessageMetadata;
import com.layer.xdk.ui.util.DisplayUtils;

public class AudioMessageMetadata extends MessageMetadata {
    
    @SerializedName("title")
    public String mTitle;

    @SerializedName("artist")
    public String mArtist;

    @SerializedName("album")
    public String mAlbum;

    @SerializedName("genre")
    public String mGenre;

    @SerializedName("mime_type")
    public String mMimeType;

    @SerializedName("source_url")
    public String mSourceUrl;

    @SerializedName("size")
    public long mSize;

    @SerializedName("preview_url")
    public String mPreviewUrl;

    @Dimension
    @SerializedName("preview_width")
    public int mPreviewWidth;

    @Dimension
    @SerializedName("preview_height")
    public int mPreviewHeight;

    @SerializedName("duration")
    public double mDuration;

    @Dimension
    public int getPreviewWidth() {
        return DisplayUtils.dpToPx(mPreviewWidth);
    }

    @Dimension
    public int getPreviewHeight() {
        return DisplayUtils.dpToPx(mPreviewHeight);
    }
}
