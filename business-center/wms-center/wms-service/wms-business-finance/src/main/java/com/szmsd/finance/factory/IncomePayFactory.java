package com.szmsd.finance.factory;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.finance.domain.AccountBalanceChange;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.dto.BalanceDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.finance.factory.abstractFactory.AbstractPayFactory;
import com.szmsd.finance.service.IAccountBalanceService;
import com.szmsd.finance.service.IAccountSerialBillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 充值
 */
@Slf4j
@Component
public class IncomePayFactory extends AbstractPayFactory {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IAccountSerialBillService accountSerialBillService;

    private ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap<>();


    @Override
    @Transactional
    public Boolean updateBalance(final CustPayDTO dto) {
        log.info("IncomePayFactory {}", JSONObject.toJSONString(dto));
        String key = "cky-test-fss-balance-" + dto.getCurrencyCode() + ":" + dto.getCusCode();
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.tryLock(time, unit)) {
                BalanceDTO oldBalance = getBalance(dto.getCusCode(), dto.getCurrencyCode());
                BigDecimal changeAmount = dto.getAmount();
                //余额不足
                if (dto.getPayType() == BillEnum.PayType.PAYMENT && oldBalance.getCurrentBalance().compareTo(changeAmount) < 0) {
                    return false;
                }

                String mKey = key + oldBalance.getVersion();

                if(concurrentHashMap.get(mKey) != null){
                    concurrentHashMap.remove(mKey);

                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        log.info("释放redis锁 {}",dto.getNo());
                        lock.unlock();
                    }

                    Thread.sleep(100);

                    log.info("balance 重新执行 {}",mKey);
                    return updateBalance(dto);
                }

                // BalanceDTO result = calculateBalance(oldBalance, changeAmount);
                oldBalance.rechargeAndSetAmount(changeAmount);
                super.addForCreditBillAsync(oldBalance.getCreditInfoBO().getRepaymentAmount(), dto.getCusCode(), dto.getCurrencyCode());
                setBalance(dto.getCusCode(), dto.getCurrencyCode(), oldBalance, true);
                recordOpLogAsync(dto, oldBalance.getCurrentBalance());
                setSerialBillLog(dto);
                recordDetailLogAsync(dto, oldBalance);

                concurrentHashMap.put(mKey,oldBalance.getVersion());

                //iAccountBalanceService.reloadCreditTime(Arrays.asList(dto.getCusCode()), dto.getCurrencyCode());
                return true;
            } else {
                log.error("充值操作超时,请稍候重试{}", JSONObject.toJSONString(dto));
                throw new RuntimeException("充值操作超时,请稍候重试");
            }
        } catch (InterruptedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //手动回滚事务
            e.printStackTrace();
            log.error("IncomePay异常:", e);
            log.info("获取余额异常，加锁失败");
            log.info("异常信息:" + e.getMessage());
            throw new RuntimeException("充值操作超时,请稍候重试!");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    protected void setOpLogAmount(AccountBalanceChange accountBalanceChange, BigDecimal amount) {
        accountBalanceChange.setAmountChange(amount);
    }

    @Override
    public BalanceDTO calculateBalance(BalanceDTO oldBalance, BigDecimal changeAmount) {
        oldBalance.setCurrentBalance(oldBalance.getCurrentBalance().add(changeAmount));
        oldBalance.setTotalBalance(oldBalance.getTotalBalance().add(changeAmount));
        return oldBalance;
    }

    @Override
    public void setSerialBillLog(CustPayDTO dto) {
        log.info("setSerialBillLog {}", JSONObject.toJSONString(dto));
        AccountSerialBillDTO serialBill = BeanMapperUtil.map(dto, AccountSerialBillDTO.class);
        serialBill.setNo(dto.getNo());
        serialBill.setChargeCategory(dto.getPayMethod().getPaymentName());
        serialBill.setChargeType(dto.getPayMethod().getPaymentName());
        serialBill.setProductCategory("充值成功");
        serialBill.setRemark(dto.getRemark());
        serialBill.setPaymentTime(new Date());
        accountSerialBillService.add(serialBill);
    }

}
