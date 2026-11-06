package com.nail.demo.solo.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.List;

import com.nail.demo.solo.common.SoloDepositFlowType;
import com.nail.demo.solo.common.SoloDepositState;
import com.nail.demo.solo.entity.DepositFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 定金台账：四条流水严格分开，余额与状态由明细推导。
 */
class DepositLedgerTest {

    private static DepositFlow flow(String type, String amount) {
        DepositFlow flow = new DepositFlow();
        flow.setType(type);
        flow.setAmount(new BigDecimal(amount));
        return flow;
    }

    @Test
    @DisplayName("未收定金：状态 NONE，余额 0")
    void empty() {
        DepositLedger ledger = DepositLedger.of(List.of());
        assertEquals(SoloDepositState.NONE, ledger.state());
        assertEquals(0, ledger.balance().signum());
    }

    @Test
    @DisplayName("收 30 元：状态 PAID，余额 30")
    void collected() {
        DepositLedger ledger = DepositLedger.of(List.of(flow(SoloDepositFlowType.COLLECT, "30")));
        assertEquals(SoloDepositState.PAID, ledger.state());
        assertEquals(new BigDecimal("30"), ledger.balance());
        assertEquals(new BigDecimal("30"), ledger.collected());
    }

    @Test
    @DisplayName("收 30 退 30：状态 REFUNDED，余额 0")
    void refunded() {
        DepositLedger ledger = DepositLedger.of(List.of(
                flow(SoloDepositFlowType.COLLECT, "30"),
                flow(SoloDepositFlowType.REFUND, "30")));
        assertEquals(SoloDepositState.REFUNDED, ledger.state());
        assertEquals(0, ledger.balance().signum());
    }

    @Test
    @DisplayName("收 30 没收 30：状态 FORFEITED")
    void forfeited() {
        DepositLedger ledger = DepositLedger.of(List.of(
                flow(SoloDepositFlowType.COLLECT, "30"),
                flow(SoloDepositFlowType.FORFEIT, "30")));
        assertEquals(SoloDepositState.FORFEITED, ledger.state());
        assertEquals(new BigDecimal("30"), ledger.forfeited());
    }

    @Test
    @DisplayName("收 30 抵扣 30：状态 OFFSET，余额 0")
    void offset() {
        DepositLedger ledger = DepositLedger.of(List.of(
                flow(SoloDepositFlowType.COLLECT, "30"),
                flow(SoloDepositFlowType.OFFSET, "30")));
        assertEquals(SoloDepositState.OFFSET, ledger.state());
        assertFalse(ledger.balance().signum() > 0);
    }

    @Test
    @DisplayName("未知类型不参与金额计算，避免脏数据放大台账")
    void unknownTypeIgnored() {
        DepositLedger ledger = DepositLedger.of(List.of(
                flow(SoloDepositFlowType.COLLECT, "30"),
                flow("UNKNOWN", "999")));
        assertEquals(new BigDecimal("30"), ledger.collected());
        assertEquals(new BigDecimal("30"), ledger.balance());
        assertEquals(SoloDepositState.PAID, ledger.state());
    }
}
