package com.amazon.proposalcalculator.writer;

import com.amazon.proposalcalculator.bean.InstanceOutput;
import com.amazon.proposalcalculator.bean.Quote;
import com.amazon.proposalcalculator.enums.LeaseContractLength;
import com.amazon.proposalcalculator.utils.SomeMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ravanini on 22/03/17.
 */
public class POIExcelWriter {

    private final static Logger LOGGER = LogManager.getLogger();

    private static List<Integer> percentageColumns;
    private static List<Integer> currencyColumns;

    static {
        percentageColumns = new ArrayList<>();
        percentageColumns.add(InstanceOutput.CPU_TOLERANCE);
        percentageColumns.add(InstanceOutput.MEMORY_TOLERANCE);

        currencyColumns = new ArrayList<>();
        currencyColumns.add(InstanceOutput.UPFRONT_FEE);
        currencyColumns.add(InstanceOutput.COMPUTE_UNIT_PRICE);
        currencyColumns.add(InstanceOutput.COMPUTE_MONTHLY_PRICE);
        currencyColumns.add(InstanceOutput.STORAGE_MONTHLY_PRICE);
        currencyColumns.add(InstanceOutput.SNAPSHOT_MONTHLY_PRICE);
        currencyColumns.add(InstanceOutput.ARCHIVE_LOGS_MONTHLY_PRICE);
        currencyColumns.add(InstanceOutput.S3_BACKUP_MONTHLY_PRICE);
    }


    public static void write(String outputFileName, List<Quote> quotes) throws IOException {
        LOGGER.info("Writing output spreadsheet...");

        XSSFWorkbook workbook = new XSSFWorkbook();

        workbook = generateSummaryTab(quotes, workbook);

        workbook = generateQuoteTabs(quotes, workbook);

        try (FileOutputStream outputStream = new FileOutputStream(outputFileName)) {
            workbook.write(outputStream);
            workbook.close();
        }

    }

    private static XSSFWorkbook generateQuoteTabs(List<Quote> quotes, XSSFWorkbook workbook) {
        for (Quote quote : quotes) {
            XSSFSheet sheet = workbook.createSheet(quote.getName());

            int rowCount = 0;
            createTitleRow(workbook, sheet, rowCount, InstanceOutput.titles);

            Row row;
            for (InstanceOutput output : quote.getOutput()) {
                row = sheet.createRow(++rowCount);

                for (int columnCount = 0; columnCount < InstanceOutput.getCollumnCount(); columnCount++) {

                    Object cellValue = output.getCell(columnCount);

                    if (cellValue instanceof String) {

                        setCell(row, columnCount, (String) cellValue);

                    }else if(cellValue instanceof Double){

                        if (isPercentageColumn(columnCount)){
                            setCellPercentage(row, columnCount, (Double) cellValue, workbook);
                        }else if (isCurrencyColumn(columnCount)){
                            setCellCurrency(row, columnCount, (Double) cellValue, workbook);
                        }else{
                            setCell(row,columnCount, (Double) cellValue);
                        }

                    }else if (cellValue instanceof Integer){

                        setCell(row, columnCount, (Integer) cellValue);//TODO ELE ESTÁ USANDO O SET CELL DE DOUBLE. VER O QUE DARÁ ISTO AQUI

                    }
                }
            }

            autoSizeColumns(sheet);

        }
        return workbook;
    }

    private static XSSFWorkbook generateSummaryTab(List<Quote> quotes, XSSFWorkbook workbook) {

        XSSFSheet sheet = workbook.createSheet(" Summary");

        String[] titles = {
                "Payment", "1yr Upfront", "3yr Upfront", /*"Monthly Formula",*/ "Monthly", "3 Years Total", "Discount"
        };

        int rowCount = 0;

        createTitleRow(workbook, sheet, rowCount, titles);

        Row row;
        for (Quote quote : quotes) {
            row = sheet.createRow(++rowCount);
            int columnCount = -1;

            setCell(row, ++columnCount, quote.getName());

            if (LeaseContractLength.ONE_YEAR.getColumnName().equals(quote.getLeaseContractLength())) {
                setCellCurrency(row, ++columnCount, SomeMath.round(quote.getUpfront(), 2), workbook);
                setCellCurrency(row, ++columnCount, 0, workbook);

            } else if (LeaseContractLength.THREE_YEARS.getColumnName().equals(quote.getLeaseContractLength())) {
                setCellCurrency(row, ++columnCount, 0, workbook);
                setCellCurrency(row, ++columnCount, SomeMath.round(quote.getUpfront(), 2), workbook);

            } else {
                setCellCurrency(row, ++columnCount, 0, workbook);
                setCellCurrency(row, ++columnCount, 0, workbook);

            }

            //setCellFormula(row, ++columnCount, quote.getMonthlyFormula(), workbook); //testing

            setCellCurrency(row, ++columnCount, SomeMath.round(quote.getMonthly(), 2), workbook);
            setCellCurrency(row, ++columnCount, SomeMath.round(quote.getThreeYearTotal(), 2), workbook);
            setCellPercentage(row, ++columnCount, SomeMath.round(quote.getDiscount(), 4), workbook);
        }

        autoSizeColumns(sheet);

        return workbook;
    }


    private static boolean isCurrencyColumn(int columnCount) {
        if (currencyColumns.contains(columnCount)){
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }

    private static boolean isPercentageColumn(int columnCount) {

        if (percentageColumns.contains(columnCount)){
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }

    private static void autoSizeColumns(XSSFSheet sheet) {
        for (int x = 0; x < sheet.getRow(0).getPhysicalNumberOfCells(); x++) {
            sheet.autoSizeColumn(x);
        }
    }

    private static Row createTitleRow(XSSFWorkbook workbook, XSSFSheet sheet, int rowCount, String[] titles) {

        XSSFFont bold = createFontBold(workbook);

        Row row = sheet.createRow(rowCount);

        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellStyle(alignCenter(workbook));
            cell.setCellValue(setFont(bold, titles[i]));
        }
        return row;
    }

    private static CellStyle alignCenter(XSSFWorkbook workbook) {
        XSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        return cellStyle;
    }

    private static void setCellPercentage(Row row, int columnCount, double rowValue, XSSFWorkbook workbook) {

        Cell cell = setCell(row, columnCount, rowValue);

        setCellPercentageStyle(cell, workbook);
    }

    private static void setCellPercentageStyle(Cell cell, XSSFWorkbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#0%"));
        cell.setCellStyle(style);
    }

    private static XSSFFont createFontBold(XSSFWorkbook workbook) {
        XSSFFont bold = workbook.createFont();
        bold.setBold(Boolean.TRUE);
        return bold;
    }

    private static XSSFRichTextString setFont(XSSFFont font, String text) {
        XSSFRichTextString richTextString = new XSSFRichTextString(text);
        richTextString.applyFont(font);
        return richTextString;
    }

    private static Cell setCell(Row row, int columnCount, String rowValue) {
        Cell cell = row.createCell(columnCount);

        cell.setCellValue(rowValue);

        return cell;
    }

    private static Cell setCell(Row row, int columnCount, double rowValue) {
        Cell cell = row.createCell(columnCount);

        cell.setCellValue(rowValue);

        return cell;
    }

    private static void setCellCurrency(Row row, int columnCount, double rowValue, XSSFWorkbook workbook) {

        Cell cell = setCell(row, columnCount, rowValue);

        CellStyle cellCurrencyStyle = workbook.createCellStyle();

        CreationHelper ch = workbook.getCreationHelper();
        cellCurrencyStyle.setDataFormat(ch.createDataFormat().getFormat("$#,#0.00"));

        cell.setCellStyle(cellCurrencyStyle);
    }

//    private void setCellDecimalFormat(Row row, int columnCount, double rowValue, XSSFWorkbook workbook) {
//
//        Cell cell = row.createCell(columnCount);
//
//        CellStyle cellDoubleStyle = getCellDecimalFormatStyle(workbook);
//
//        cell.setCellValue(rowValue);
//        cell.setCellStyle(cellDoubleStyle);
//    }

    private static void setCellFormula(Row row, int columnCount, String rowValue, XSSFWorkbook workbook) {
        Cell cell = row.createCell(columnCount);

        CellStyle cellDoubleStyle = getCellDecimalFormatStyle(workbook);

        cell.setCellFormula(rowValue);
        cell.setCellStyle(cellDoubleStyle);
    }

    private static CellStyle getCellDecimalFormatStyle(XSSFWorkbook workbook) {
        CellStyle cellDoubleStyle = workbook.createCellStyle();
        cellDoubleStyle.setDataFormat(
                workbook.getCreationHelper().createDataFormat().getFormat("#############0.00"));
        return cellDoubleStyle;
    }

}