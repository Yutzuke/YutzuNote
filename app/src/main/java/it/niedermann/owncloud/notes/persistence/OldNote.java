/*
 * Copyright MURENA SAS 2023
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.niedermann.owncloud.notes.persistence;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.Calendar;

import it.niedermann.owncloud.notes.shared.model.DBStatus;

/**
 * Entity representation for `NOTES` table.
 * This is added to migrate old `NOTES` items to new `Note` table.
 *
 * <a href="https://gitlab.e.foundation/e/os/backlog/-/issues/1147">Check for more details</a>
 */
public class OldNote implements Serializable {

    private long id;

    private long remoteId;

    @NonNull
    private DBStatus status;

    @NonNull
    private String title;

    @Nullable
    private Calendar modified;

    @NonNull
    private String content;

    private boolean favorite = false;

    @NonNull
    private String category = "";

    @Nullable
    private String eTag;

    public OldNote(long id, long remoteId, @Nullable Calendar modified, @NonNull String title, @NonNull String content, boolean favorite, @NonNull String category, @Nullable String eTag, @NonNull DBStatus status) {
        this.id = id;
        this.remoteId = remoteId;
        this.status = status;
        this.title = title;
        this.modified = modified;
        this.content = content;
        this.favorite = favorite;
        this.category = category;
        this.eTag = eTag;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(long remoteId) {
        this.remoteId = remoteId;
    }

    @NonNull
    public DBStatus getStatus() {
        return status;
    }

    public void setStatus(@NonNull DBStatus status) {
        this.status = status;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    @Nullable
    public Calendar getModified() {
        return modified;
    }

    public void setModified(@Nullable Calendar modified) {
        this.modified = modified;
    }

    @NonNull
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }

    public boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public void setCategory(@NonNull String category) {
        this.category = category;
    }

    @Nullable
    public String getETag() {
        return eTag;
    }

    public void setETag(@Nullable String eTag) {
        this.eTag = eTag;
    }
}
