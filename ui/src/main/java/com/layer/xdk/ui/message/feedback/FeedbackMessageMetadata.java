package com.layer.xdk.ui.message.feedback;

import android.net.Uri;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.layer.xdk.ui.message.MessageMetadata;

public class FeedbackMessageMetadata extends MessageMetadata {

    @SerializedName("title")
    public String mTitle;

    @SerializedName("prompt")
    public String mPrompt;

    @SerializedName("prompt_wait")
    public String mPromptWait;

    @SerializedName("placeholder")
    public String mCommentHint;

    @SerializedName("enabled_for")
    public String mEnabledFor;

    public transient boolean mEnabledForMe;

    /**
     * Sets the {@link #mEnabledForMe} flag based on the metadata and authenticated user.
     *
     * @param authenticatedUserId user ID for layer client's authenticated user
     */
    public void setEnabledForMe(@Nullable Uri authenticatedUserId) {
        if (authenticatedUserId == null) {
            mEnabledForMe = false;
            return;
        }
        String myUserID = authenticatedUserId.toString();
        mEnabledForMe = mEnabledFor == null || mEnabledFor.equals(myUserID);
    }
}
