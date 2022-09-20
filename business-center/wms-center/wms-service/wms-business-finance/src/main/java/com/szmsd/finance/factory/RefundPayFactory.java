package com.szmsd.finance.factory;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.finance.domain.AccountBalanceChange;
import com.szmsd.finance.domain.AccountSerialBill;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.dto.BalanceDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.factory.abstractFactory.AbstractPayFactory;
import com.szmsd.finance.mapper.AccountSerialBillMapper;
import com.szmsd.finance.service.IAccountSerialBillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退费
 */
@Slf4j
@Component
public class RefundPayFactory extends AbstractPayFactory {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IAccountSerialBillService accountSerialBillService;
    @Autowired
    private AccountSerialBillMapper accountSerialBillMapper;

    private ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Boolean updateBalance(final CustPayDTO dto) {
        log.info("RefundPayFactory {}", JSONObject.toJSONString(dto));
        String key = "cky-test-fss-balance-" + dto.getCurrencyCode() + ":" + dto.getCusCode();
        RLock lock = redissonClient.getLock(key);
        try {
            boolean success = false;
            if (lock.tryLock(time, unit)) {
                BalanceDTO oldBalance = getBalance(dto.getCusCode(), dto.getCurrencyCode());

                String mKey = key + oldBalance.getVersion();

                log.info("balance mKey version {}",mKey);

                if(concurrentHashMap.get(mKey) != null){
                    concurrentHashMap.remove(mKey);

                    //Thread.sleep(100);

                    log.info("balance 重新执行 {}",mKey);
                    return updateBalance(dto);
                }

                log.info("【退费】RefundPayFactory-- {}",JSONObject.toJSONString(oldBalance));
                BigDecimal changeAmount = dto.getAmount();
                if (changeAmount.compareTo(BigDecimal.ZERO) >= 0) {
                    // 充值
                    success = oldBalance.rechargeAndSetAmount(changeAmount);
                    super.addForCreditBillAsync(oldBalance.getCreditInfoBO().getRepaymentAmount(), dto.getCusCode(), dto.getCurrencyCode());
                } else {
                    // 退费强制扣钱
                    success = oldBalance.payAnyWay(changeAmount.negate());
                }
                if (!success){
                    return false;
                }
                BalanceDTO result = oldBalance;
                setBalance(dto.getCusCode(), dto.getCurrencyCode(), result, true);
                recordOpLogAsync(dto, result.getCurrentBalance());
                recordDetailLogAsync(dto, oldBalance);
                setSerialBillLog(dto);

                concurrentHashMap.put(mKey,oldBalance.getVersion());

                return true;
            } else {
                log.error("退费业务处理超时,请稍候重试{}", JSONObject.toJSONString(dto));
                throw new RuntimeException("退费业务处理超时,请稍候重试");
            }
        } catch (InterruptedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //手动回滚事务
            e.printStackTrace();
            log.error("RefundPayFactory异常:", e);
            log.info("获取余额异常，加锁失败");
            log.info("异常信息:" + e.getMessage());
            throw new RuntimeException("退费业务处理超时,请稍候重试!");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("【退费】RefundPayFactory 解锁结束--");
            }
            log.info("【退费】RefundPayFactory --结束--");
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
        financeThreadTaskPool.execute(() -> {
            log.info("setSerialBillLog {}", JSONObject.toJSONString(dto));
            List<AccountSerialBillDTO> serialBillInfoList = dto.getSerialBillInfoList();
            AccountSerialBillDTO serialBill = serialBillInfoList.get(0);
            serialBill.setNo(dto.getNo());
            serialBill.setRemark(dto.getRemark());
            serialBill.setWarehouseCode(dto.getWarehouseCode());
            serialBill.setWarehouseName(dto.getWarehouseName());
            serialBill.setPaymentTime(new Date());
            AccountSerialBill accountSerialBill = new AccountSerialBill();
            BeanUtils.copyProperties(serialBill, accountSerialBill);
            if (StringUtils.isNotBlank(dto.getNo())) {
                DelOutbound delOutbound = accountSerialBillMapper.selectDelOutbound(dto.getNo());
                if (delOutbound!=null) {
                    if (delOutbound.getId() != null) {
                        accountSerialBill.setRefNo(delOutbound.getRefNo());
                        accountSerialBill.setShipmentService(delOutbound.getShipmentService());
                        accountSerialBill.setWeight(delOutbound.getWeight());
                        accountSerialBill.setCalcWeight(delOutbound.getCalcWeight());
                        accountSerialBill.setSpecifications(delOutbound.getSpecifications());
                    }
                }
            }

                accountSerialBillService.save(accountSerialBill);
            //accountSerialBillService.add(serialBill);
        });
    }

}
