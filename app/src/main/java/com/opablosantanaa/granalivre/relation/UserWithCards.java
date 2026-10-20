package com.opablosantanaa.granalivre.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.User;

import java.util.List;

public class UserWithCards {
    @Embedded
    public User user;

    @Relation(parentColumn = "user_id", entityColumn = "user_id")
    public List<Card> cards;
}
