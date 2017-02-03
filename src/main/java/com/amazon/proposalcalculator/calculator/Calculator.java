package com.amazon.proposalcalculator.calculator;

import com.amazon.proposalcalculator.assemblies.DefaultOutputAssembly;
import com.amazon.proposalcalculator.bean.DefaultInput;
import com.amazon.proposalcalculator.bean.DefaultOutput;
import com.amazon.proposalcalculator.bean.Price;
import com.amazon.proposalcalculator.exception.PricingCalculatorException;
import com.amazon.proposalcalculator.utils.Constants;
import com.amazon.proposalcalculator.writer.DefaultExcelWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.amazon.proposalcalculator.calculator.CalculatorPredicates.*;
import static java.time.temporal.ChronoUnit.DAYS;

public class Calculator {

	private final static Logger LOGGER = LogManager.getLogger();

	public static void calculate() {
		Constants.output = new ArrayList<DefaultOutput>();
        LOGGER.info("Calculating prices...");

		for (DefaultInput input
				: Constants.servers) {

			LOGGER.debug("Calculating instance: " + input.getDescription());
			DefaultOutput output = DefaultOutputAssembly.from(input);

			try {

				List<Price> possibleMatches = Constants.ec2PriceList.stream().filter(region(input)
						.and(tenancy(input))
						.and(operatingSystem(input))
						.and(preInstalledSw(input))
						.and(termType(input))
						.and(offeringClass(input))
						.and(leaseContractLength(input))
						.and(purchaseOption(input))
						.and(cpuTolerance(input))
						.and(memory(input))

				).collect(Collectors.toList());

				Price price = getBestPrice(possibleMatches);
				
				output.setInstanceType(price.getInstanceType());
				output.setInstanceMemory(price.getMemory());
				output.setInstanceVCPU(price.getvCPU());
				output.setComputeUnitPrice(price.getInstanceHourPrice());
				
				//output.setComputeMonthlyPrice(
				//		price.getInstanceHourPrice() * Constants.hoursInAMonth * input.getCpuUsage() / 100 * input.getInstances());
				
				output.setComputeMonthlyPrice(price.getInstanceHourPrice() * Constants.hoursInAMonth * input.getInstances() * (input.getTermType().equals("OnDemand") ? input.getMonthlyUtilization() / 100 : 1));
				//LOGGER.info("Calculo:" + input.getDescription() + " " +  (input.getTermType().equals("OnDemand") ? input.getMonthlyUtilization() / 100 : 1) );
				
				long days = diffInDays(input.getBeginning(), input.getEnd());
				//output.setComputeTotalPrice(price.getInstanceHourPrice() * days * 24 * input.getCpuUsage() / 100 * input.getInstances());
				
				output.setComputeTotalPrice(price.getInstanceHourPrice() * days * 24 * input.getInstances() * (input.getTermType().equals("OnDemand") ? input.getMonthlyUtilization() / 100 : 1));

				output.setStorageMonthlyPrice(StoragePricingCalculator.getStorageMonthlyPrice(input));
				output.setSnapshotMonthlyPrice(StoragePricingCalculator.getSnapshotMonthlyPrice(input));
				
				output.setUpfrontFee(price.getUpfrontFee());

			} catch (PricingCalculatorException pce){
				output.setErrorMessage(pce.getMessage());
			}
			Constants.output.add(output);

		}
		DefaultExcelWriter.write();
	}
	
	private static void setEfectivePrice(List<Price> priceList) {
		for (Price somePrice : priceList) {
			if (somePrice.getTermType().equals("OnDemand") || somePrice.getPurchaseOption().equals("No Upfront")) {
				somePrice.setEfectivePrice(somePrice.getPricePerUnit());
				somePrice.setInstanceHourPrice(somePrice.getPricePerUnit());
			} else if (somePrice.getPurchaseOption().equals("Partial Upfront")
					|| somePrice.getPurchaseOption().equals("All Upfront")) {
				if (somePrice.getPriceDescription().equals("Upfront Fee")) {
					Price hourFee = getInstanceHourFee(priceList, somePrice);
					somePrice.setEfectivePrice(somePrice.getPricePerUnit()/(12*Integer.valueOf(somePrice.getLeaseContractLength().substring(0, 1))) + hourFee.getPricePerUnit());
					somePrice.setUpfrontFee(somePrice.getPricePerUnit());
					somePrice.setInstanceHourPrice(hourFee.getPricePerUnit());
				} else {
					Price upfrontFee = getUpfrontFee(priceList, somePrice);
					somePrice.setEfectivePrice(upfrontFee.getPricePerUnit()/(12*Integer.valueOf(somePrice.getLeaseContractLength().substring(0, 1))) + somePrice.getPricePerUnit());
					somePrice.setUpfrontFee(upfrontFee.getPricePerUnit());
					somePrice.setInstanceHourPrice(somePrice.getPricePerUnit());
				}
			}
		}
	}
	
	private static Price getUpfrontFee(List<Price> priceList, Price price) {
		for (Price somePrice : priceList) {
			if (somePrice.getSku().equals(price.getSku()) && somePrice.getPriceDescription().equals("Upfront Fee")) {
				return somePrice;
			}
		}
		return null;
	}
	
	private static Price getInstanceHourFee(List<Price> priceList, Price price) {
		for (Price somePrice : priceList) {
			if (somePrice.getSku().equals(price.getSku()) && !somePrice.getPriceDescription().equals("Upfront Fee")) {
				return somePrice;
			}
		}
		return null;
	}

	private static Price getBestPrice(List<Price> prices) {
		
		setEfectivePrice(prices);
		
		Price bestPrice = new Price();
		bestPrice.setEfectivePrice(1000000);
		
		for (Price price : prices) {
			if (price.getEfectivePrice() < bestPrice.getEfectivePrice()) {
				bestPrice = price;
			}
		}
		
		return bestPrice;
	}
	
	private static long diffInDays(String beginning, String end) {
		try {
			SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
			Date beginningDate = format.parse(beginning);
			Date endDate = format.parse(end);
			return diffInDays(beginningDate, endDate);
		} catch (java.text.ParseException e) {
			e.printStackTrace();
			return 0;
		}
	}

	private static long diffInDays(Date beginning, Date end) {

		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(beginning);
		LocalDate localBeginning = LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
				calendar.get(Calendar.DAY_OF_MONTH));
		calendar.setTime(end);
		LocalDate localEnd = LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
				calendar.get(Calendar.DAY_OF_MONTH));
		return DAYS.between(localBeginning, localEnd.plusDays(1));//last day inclusive
	}


}
