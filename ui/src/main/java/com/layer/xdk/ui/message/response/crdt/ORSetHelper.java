package com.layer.xdk.ui.message.response.crdt;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import com.layer.xdk.ui.message.response.ResponseSummaryMetadataV2;
import com.layer.xdk.ui.message.response.SummaryAddOperationMetadata;
import com.layer.xdk.ui.message.response.SummaryStateMetadata;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to use when dealing with OR Sets that back message selections. This also persists
 * a local state object to the {@link Message}'s local data field for offline support.
 */
public abstract class ORSetHelper {

    private ORSetSummary mORSetSummary;
    private final Gson mGson;
    private final MessagePart mRootPart;

    /**
     * @param gson Gson object to use for serialization/de-serialization to the local data field of
     *             the Message
     * @param rootPart the root message part these selections belong to
     */
    public ORSetHelper(@NonNull Gson gson, @NonNull MessagePart rootPart) {
        mGson = gson;
        mRootPart = rootPart;
        initializeORSetSummary();
    }

    /**
     * Merge a response summary into to local OR set. This updates the local set based on server
     * data. This local set is then written to the Message's local data field.
     *
     * @param metadata summary to merge into the local set
     */
    public void processRemoteResponseSummary(ResponseSummaryMetadataV2 metadata) {
        for (Map.Entry<String, Map<String, SummaryStateMetadata>> userEntry :
                metadata.entrySet()) {
            String userId = userEntry.getKey();
            Map<String, SummaryStateMetadata> states = userEntry.getValue();
            for (Map.Entry<String, SummaryStateMetadata> stateEntry : states.entrySet()) {
                String stateName = stateEntry.getKey();
                SummaryStateMetadata stateMetadata = stateEntry.getValue();
                ORSet orSet = getOrCreateAndAddORSet(userId, stateName);
                orSet.synchronize(convertResponseSummary(stateName, stateMetadata));
            }
        }
        cacheORSetSummary();
    }

    /**
     * Update the local set based on a selection that was made by the user. The updated local set
     * is then written to the Message's local data field.
     *
     * @param identityId full identity ID of the user that made this change
     * @param responseName the response name that this change belongs to
     * @param selected true if the value is being selected, false if it is being deselected
     * @param value the value being selected
     * @return a list of operation results that denote the changes. Usually used to send to the
     * server
     */
    public List<OrOperationResult> processLocalSelection(@NonNull Uri identityId,
            @NonNull String responseName,
            boolean selected,
            @NonNull String value) {
        ORSet set = getOrCreateAndAddORSet(identityId.toString(), responseName);
        List<OrOperationResult> result = null;
        if (selected) {
            result = set.addOperation(new OrOperation(value));
        } else {
            List<String> idsToRemove = set.findAddOperationsWithValue(value);
            for (String id : idsToRemove) {
                List<OrOperationResult> removeResult = set.removeOperation(id);
                if (removeResult != null) {
                    if (result == null) {
                        result = removeResult;
                    } else {
                        result.addAll(removeResult);
                    }
                }
            }
        }
        cacheORSetSummary();
        return result;
    }

    /**
     * Get the selections a specific user.
     *
     * @param userId the full identity ID for the user to get selections for
     *               (i.e. "layer:///identities/{user-id}")
     * @param responseName response name to search for
     * @return the selected values or an empty set if none are selected
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public Set<String> getSelections(String userId, String responseName) {
        ORSet set = mORSetSummary.getSet(userId, responseName);
        if (set != null) {
            return set.getSelectedValues();
        }
        return Collections.emptySet();
    }

    /**
     * Create an appropriate OR set, seeding it with add/remove sets if desired.
     *
     * @param responseName the property name to use for the set
     * @param adds optional set to prepopulate the set's add operations with
     * @param removes optional set to prepopulate the set's remove operation IDs with
     * @return a new {@link ORSet}
     */
    protected abstract ORSet createORSet(String responseName, LinkedHashSet<OrOperation> adds,
            LinkedHashSet<String> removes);


    /**
     * Load the {@link ORSetSummary} for this message part from the Message's local data field. If
     * one doesn't exist then create one.
     */
    private void initializeORSetSummary() {
        PartSummaries partSummaries = getPartSummariesFromLocalData();
        if (partSummaries != null) {
            mORSetSummary = partSummaries.get(mRootPart.getId().toString());
        }
        if (mORSetSummary == null) {
            mORSetSummary = new ORSetSummary();
        }
    }

    /**
     * Convert a response summary into an OR set.
     *
     * @param stateName the name of the state object in the summary
     * @param metadata the corresponding state POJO
     * @return an {@link ORSet} that represents this summary
     */
    @Nullable
    private ORSet convertResponseSummary(@NonNull String stateName,
            @Nullable SummaryStateMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        LinkedHashSet<OrOperation> adds = new LinkedHashSet<>();
        if (metadata.mAddOperations != null) {
            for (SummaryAddOperationMetadata addOperation : metadata.mAddOperations) {
                for (String id : addOperation.mIds) {
                    adds.add(new OrOperation(addOperation.mValue, id));
                }
            }
        }
        List<String> removeIds = metadata.mRemoveIds;
        LinkedHashSet<String> removes = new LinkedHashSet<>();
        if (metadata.mRemoveIds != null) {
            removes.addAll(removeIds);
        }
        return createORSet(stateName, adds, removes);
    }


    /**
     * @return a {@link PartSummaries} object from the Message's local data field, or null if none
     * exists
     */
    @Nullable
    private PartSummaries getPartSummariesFromLocalData() {
        byte[] messageLocalData = mRootPart.getMessage().getLocalData();
        if (messageLocalData != null) {
            String json = new String(messageLocalData, Charset.forName("UTF-8"));
            return mGson.fromJson(json, PartSummaries.class);
        }
        return null;
    }

    /**
     * Persists an {@link ORSetSummary} to the message's local data field, indexed by this part's
     * ID.
     */
    private void cacheORSetSummary() {
        PartSummaries partSummaries = getPartSummariesFromLocalData();
        if (partSummaries == null) {
            partSummaries = new PartSummaries();
        }
        partSummaries.put(mRootPart.getId().toString(), mORSetSummary);
        byte[] newPartSummaries = mGson.toJson(partSummaries).getBytes(Charset.forName("UTF-8"));
        mRootPart.getMessage().putLocalData(newPartSummaries);
    }

    /**
     * Gets an existing OR Set with the given arguments or creates an appropriate implementation
     * using {@link #createORSet(String, LinkedHashSet, LinkedHashSet)}. If an implementation is
     * created, it is added to the {@link ORSetSummary} object.
     *
     * @param identityId the full identity ID to search for
     * @param stateName the state name to search for
     * @return the existing {@link ORSet} or a new one if none exists
     */
    @NonNull
    private ORSet getOrCreateAndAddORSet(String identityId, String stateName) {
        ORSet orSet = mORSetSummary.getSet(identityId, stateName);
        if (orSet == null) {
            // Instantiate correct set and add to the summary
            orSet = createORSet(stateName, null, null);
            mORSetSummary.addSet(identityId, stateName, orSet);
        }
        return orSet;
    }

    /**
     * POJO that represents saved {@link ORSetSummary} objects keyed by {@link MessagePart} IDs.
     */
    private static class PartSummaries extends HashMap<String, ORSetSummary> {}
}
