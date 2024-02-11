package com.renomad.inmra.auth;


import com.renomad.minum.database.DbData;

import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * A data structure representing authentication information for a user.
 */
public class User extends DbData<User> {

    public static final User EMPTY = new User(0L, "", "", "");
    private Long id;
    private final String username;
    private final String hashedPassword;
    private final String salt;

    /**
     * @param id the unique identifier for this record
     *  @param username a user-chosen unique identifier for this record (system must not allow two of the same username)
     *  @param hashedPassword the hash of a user's password
     *  @param salt some randomly-generated letters appended to the user's password.  This is
     *              to prevent dictionary attacks if someone gets access to the database contents.
     *              See "Salting" in docs/http_protocol/password_storage_cheat_sheet.txt
     */
    public User(Long id, String username, String hashedPassword, String salt) {

        this.id = id;
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.salt = salt;
    }

    @Override
    public long getIndex() {
        return id;
    }

    @Override
    protected void setIndex(long index) {
        this.id = index;
    }

    @Override
    public String serialize() {
        return serializeHelper(id, username, hashedPassword, salt);
    }

    @Override
    public User deserialize(String serializedText) {
        final var tokens = deserializeHelper(serializedText);
        return new User(
                Long.parseLong(tokens.get(0)),
                tokens.get(1),
                tokens.get(2),
                tokens.get(3)
                );
    }

    public String getUsername() {
        return username;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public String getSalt() {
        return salt;
    }

}
