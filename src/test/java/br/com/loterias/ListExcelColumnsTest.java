package br.com.loterias;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;

public class ListExcelColumnsTest {
    @Test
    void listColumns() throws Exception {
        Path dir = Path.of("src/main/resources/excels");
        for (File f : dir.toFile().listFiles()) {
            if (f.getName().endsWith(".xlsx")) {
                System.out.println("\n=== " + f.getName() + " ===");
                try (Workbook wb = new XSSFWorkbook(new FileInputStream(f))) {
                    Sheet sheet = wb.getSheetAt(0);
                    Row header = sheet.getRow(0);
                    for (int i = 0; i < header.getLastCellNum(); i++) {
                        Cell cell = header.getCell(i);
                        String val = cell != null ? cell.toString().trim().replace("\n", " ") : "";
                        System.out.println(i + ": " + val);
                    }
                }
            }
        }
    }
}
