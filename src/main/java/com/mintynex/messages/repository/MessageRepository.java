package com.mintynex.messages.repository;

import com.mintynex.messages.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Conversation between two users
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender.id = :a AND m.receiver.id = :b) OR " +
           "(m.sender.id = :b AND m.receiver.id = :a) " +
           "ORDER BY m.createdAt ASC")
    Page<Message> findConversation(@Param("a") Long userA,
                                   @Param("b") Long userB,
                                   Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.sender.id = :senderId AND m.receiver.id = :receiverId AND m.read = false")
    void markAsRead(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);

    long countBySenderIdAndReceiverIdAndReadFalse(Long senderId, Long receiverId);
}
