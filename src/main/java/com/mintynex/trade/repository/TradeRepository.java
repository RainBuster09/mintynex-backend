package com.mintynex.trade.repository;

import com.mintynex.trade.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByProposerIdOrReceiverIdOrderByCreatedAtDesc(Long proposerId, Long receiverId);

    List<Trade> findByStatusOrderByCreatedAtDesc(Trade.Status status);
}