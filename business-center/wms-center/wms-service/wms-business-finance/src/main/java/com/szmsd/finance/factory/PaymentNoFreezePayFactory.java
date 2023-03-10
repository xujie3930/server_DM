package com.szmsd.finance.factory;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.bas.api.service.SerialNumberClientService;
import com.szmsd.finance.domain.AccountBalanceChange;
import com.szmsd.finance.dto.BalanceDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.finance.factory.abstractFactory.AbstractPayFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 直接扣费不涉及冻结
 */
@Slf4j
@Component
public class PaymentNoFreezePayFactory extends AbstractPayFactory {

    @Autowired
    SerialNumberClientService serialNumberClientService;

    @Resource
    private RedissonClient redissonClient;

    private ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap<>();


    @Transactional
    @Override
    public Boolean updateBalance(final CustPayDTO dto) {
        log.info("PaymentNoFreezePayFactory {}", JSONObject.toJSONString(dto));
        final String key = "cky-fss-freeze-balance-all:" + dto.getCusCode();
        //RLock lock = redissonClient.getLock(key);
        try {
            //if (lock.tryLock(time,leaseTime, unit)) {
                String currencyCode = dto.getCurrencyCode();
                BalanceDTO oldBalance = getBalance(dto.getCusCode(), dto.getCurrencyCode());
                BigDecimal changeAmount = dto.getAmount();

                log.info("PaymentNoFreezePayFactory balance mKey version  1 {}",dto.getNo());

                log.info("PaymentNoFreezePayFactory 1 {} 可用余额：{}，冻结余额：{}，总余额：{},余额剩余：{} ",currencyCode,oldBalance.getCurrentBalance(),oldBalance.getFreezeBalance(),oldBalance.getTotalBalance(),JSONObject.toJSONString(oldBalance));

                //余额不足
                /*if (dto.getPayType() == BillEnum.PayType.PAYMENT_NO_FREEZE && oldBalance.getCurrentBalance().compareTo(changeAmount) < 0) {
                    return false;
                }
                BalanceDTO result = calculateBalance(oldBalance, changeAmount);*/
                if (dto.getPayType() == BillEnum.PayType.PAYMENT_NO_FREEZE && !oldBalance.checkAndSetAmountAndCreditAnd(changeAmount, true, BalanceDTO::pay)) {
                    return false;
                }

                String mKey = key + oldBalance.getVersion();

//                if(concurrentHashMap.get(mKey) != null){
//                    concurrentHashMap.remove(mKey);
//
//                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
//                        log.info("释放redis锁 {}",dto.getNo());
//                        lock.unlock();
//                    }
//
//                    Thread.sleep(100);
//
//                    log.info("balance 重新执行 {}",mKey);
//                    return updateBalance(dto);
//                }

                setBalance(dto.getCusCode(), dto.getCurrencyCode(), oldBalance, true);
                recordOpLogAsync(dto, oldBalance.getCurrentBalance());
                recordDetailLogAsync(dto, oldBalance);
                setSerialBillLog(dto);

                log.info("PaymentNoFreezePayFactory 2 {} 可用余额：{}，冻结余额：{}，总余额：{},余额剩余：{} ",currencyCode,oldBalance.getCurrentBalance(),oldBalance.getFreezeBalance(),oldBalance.getTotalBalance(),JSONObject.toJSONString(oldBalance));

                concurrentHashMap.put(mKey,oldBalance.getVersion());

                return true;
//            } else {
//                log.error("支付操作超时,请稍候重试{}", JSONObject.toJSONString(dto));
//                throw new RuntimeException("支付操作超时,请稍候重试");
//            }
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //手动回滚事务
            e.printStackTrace();
            log.error("PaymentNoFreeze扣减异常:", e);
            log.info("获取余额异常，加锁失败");
            log.info("异常信息:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        } finally {
//            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
//                lock.unlock();
//            }
        }
    }

    @Override
    protected void setOpLogAmount(AccountBalanceChange accountBalanceChange, BigDecimal amount) {
        accountBalanceChange.setAmountChange(amount.negate());
    }

    @Override
    public BalanceDTO calculateBalance(BalanceDTO oldBalance, BigDecimal changeAmount) {
        // 可用
        oldBalance.setCurrentBalance(oldBalance.getCurrentBalance().subtract(changeAmount));
        // 总余额
        oldBalance.setTotalBalance(oldBalance.getTotalBalance().subtract(changeAmount));
        return oldBalance;
    }

}
