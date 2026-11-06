package com.nail.demo.solo.support;

import java.math.BigDecimal;
import java.util.List;

import com.nail.demo.solo.common.SoloDepositFlowType;
import com.nail.demo.solo.common.SoloDepositState;
import com.nail.demo.solo.entity.DepositFlow;

/**
 * 定金台账：四条流水各自合计，余额与状态全部由明细推导。
 */
public record DepositLedger(BigDecimal collected,
                            BigDecimal offset,
                            BigDecimal refunded,
                            BigDecimal forfeited,
                            BigDecimal balance,
                            String state) {

    public static DepositLedger of(List<DepositFlow> flows) {
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal offset = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal forfeited = BigDecimal.ZERO;

        for (DepositFlow flow : flows) {
            BigDecimal amount = flow.getAmount() == null ? BigDecimal.ZERO : flow.getAmount();
            switch (flow.getType()) {
                case SoloDepositFlowType.COLLECT -> collected = collected.add(amount);
                case SoloDepositFlowType.OFFSET -> offset = offset.add(amount);
                case SoloDepositFlowType.REFUND -> refunded = refunded.add(amount);
                case SoloDepositFlowType.FORFEIT -> forfeited = forfeited.add(amount);
                default -> {
                    // 未知类型不参与台账计算，避免脏数据放大金额。
                }
            }
        }

        BigDecimal balance = collected.subtract(offset).subtract(refunded).subtract(forfeited);
        return new DepositLedger(collected, offset, refunded, forfeited, balance, resolveState(
                collected, offset, refunded, forfeited, balance));
    }

    public static DepositLedger empty() {
        return new DepositLedger(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, SoloDepositState.NONE);
    }

    private static String resolveState(BigDecimal collected, BigDecimal offset,
                                       BigDecimal refunded, BigDecimal forfeited, BigDecimal balance) {
        if (collected.signum() == 0 && offset.signum() == 0
                && refunded.signum() == 0 && forfeited.signum() == 0) {
            return SoloDepositState.NONE;
        }
        if (forfeited.signum() > 0 && balance.signum() <= 0) {
            return SoloDepositState.FORFEITED;
        }
        if (refunded.signum() > 0 && balance.signum() <= 0) {
            return SoloDepositState.REFUNDED;
        }
        if (offset.signum() > 0) {
            return SoloDepositState.OFFSET;
        }
        if (collected.signum() > 0) {
            return SoloDepositState.PAID;
        }
        return SoloDepositState.NONE;
    }
}
