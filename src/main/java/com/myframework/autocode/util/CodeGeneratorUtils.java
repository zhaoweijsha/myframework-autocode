package com.myframework.autocode.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.myframework.autocode.config.Config;
import com.myframework.autocode.config.DbTypeEnum;
import com.myframework.autocode.generator.GeneratorCreateSql;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.myframework.autocode.entity.ColumnInfo;
import com.myframework.autocode.entity.TableInfo;

public class CodeGeneratorUtils {

    public static List<TableInfo> parseTable() throws IOException {
        if ("1".equals(Config.GENERATE_TYPE)) {
            File xlsxFile = new File(System.getProperty("user.dir") + Config.DB_DEFINE_FILE);
            List<TableInfo> tableInfoList = CodeGeneratorUtils.readXlsx(xlsxFile);
            System.out.println(tableInfoList);
            return tableInfoList;
        } else if ("2".equals(Config.GENERATE_TYPE)) {
            List<TableInfo> tableInfoList = CodeGeneratorUtils.readDb(Config.DB_TABLES.split(","), Config.DB_TYPE);
            System.out.println(tableInfoList);
            return tableInfoList;
        }
        return null;
    }

    public static void outFile(File file, String template, Map dataMap) throws IOException, TemplateException {
        Configuration cfg = new Configuration();
        String tpmlPath = new String("/com/myframework/autocode/template/").replaceAll("/", File.separator);
        cfg.setClassForTemplateLoading(CodeGeneratorUtils.class.getClassLoader().getClass(), tpmlPath);
        file.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        Writer writer = new OutputStreamWriter(fos, "UTF-8");
        Template t = cfg.getTemplate(template);
        t.process(dataMap, writer);
        fos.flush();
        fos.close();
        writer.close();
        System.out.println("导出成功：" + file.getAbsolutePath());
    }

    /**
     * 读取xlsx配置文件用
     * <p>
     * 读取指定的文件并遍历所有页签，找到符合规则的页签，读取其中的数据表信息
     */
    public static List<TableInfo> readXlsx(File xlsxFile) throws IOException {
        InputStream is = new FileInputStream(xlsxFile);
        XSSFWorkbook wb = new XSSFWorkbook(is);
        int sheetNum = wb.getNumberOfSheets();
        Pattern pattern = Pattern.compile("^(.+)\\(([0-9a-zA-Z_]+)\\)$"); // 页签名称正则表达式，例如“用户信息表(user)”
        List<TableInfo> tableList = new ArrayList<TableInfo>();
        for (int i = 0; i < sheetNum; i++) {
            XSSFSheet sheet = wb.getSheetAt(i);
            Matcher matcher = pattern.matcher(sheet.getSheetName().toString());
            if (matcher.matches()) {
                TableInfo tableInfo = new TableInfo();
                tableInfo.setTableName(matcher.group(1).trim()); // 第一部分是表名称
                tableInfo.setTableDbName(matcher.group(2).replace(Config.DB_PREFIX, "").trim()); // 第二部分是数据库内的表名称
                String moduleName = Config.MODULE_NAME;
                if (StringUtils.isEmpty(moduleName)) {
                    if (matcher.group(2).replace(Config.DB_PREFIX, "").contains("_")) {
                        moduleName = matcher.group(2).replace(Config.DB_PREFIX, "")
                                .substring(0, matcher.group(2).indexOf("_"));
                    } else {
                        moduleName = matcher.group(2).replace(Config.DB_PREFIX, "").toLowerCase();
                    }
                }
                tableInfo.setModuleName(moduleName.toLowerCase());
                List<ColumnInfo> columnList = new ArrayList<ColumnInfo>();
                for (int j = 1; j <= sheet.getLastRowNum(); j++) {
                    XSSFRow row = sheet.getRow(j);
                    if (row == null || row.getCell(0) == null || "".equals(row.getCell(0).getStringCellValue())) {
                        continue;
                    }
                    ColumnInfo column = new ColumnInfo();
                    column.setName(row.getCell(0).getStringCellValue()); // 表格第一列：元素名称
                    column.setKey("是".equals(row.getCell(1).getStringCellValue().trim()));// 表格第二列：是否主键
                    column.setNotNull("是".equals(row.getCell(2).getStringCellValue().trim())); // 表格第三列：是否非空
                    column.setType(row.getCell(3).getStringCellValue()); // 表格第四列：类型
                    column.setLength((int) (row.getCell(4).getNumericCellValue())); // 表格第五列：长度
                    column.setDescription(row.getCell(5).getStringCellValue()); // 表格第六列：描述

                    // 表个第七列：示例
                    XSSFCell xssfCell = row.getCell(6);
                    String value = "";
                    if (xssfCell != null) {
                        switch (xssfCell.getCellType()) {
                            case XSSFCell.CELL_TYPE_STRING:
                                value = xssfCell.getStringCellValue();
                                break;
                            case XSSFCell.CELL_TYPE_NUMERIC:
                                value = String.valueOf((int) xssfCell.getNumericCellValue());
                            default:
                        }
                    }
                    column.setExample(value);

                    columnList.add(column);
                }
                tableInfo.setColumns(columnList);
                tableList.add(tableInfo);
            }
        }
        return tableList;
    }

    public static List<TableInfo> readDb(String[] tableNames, DbTypeEnum dbTypeEnum) {
        List<TableInfo> tableList = new ArrayList<TableInfo>();
        if (DbTypeEnum.MYSQL == dbTypeEnum) {
            for (int i = 0; i < tableNames.length; i++) {
                Map table = DBHelper.queryOne("SELECT " +
                        " table_name tableName , " +
                        " ENGINE , " +
                        " table_comment tableComment , " +
                        " create_time createTime " +
                        "FROM " +
                        " information_schema. TABLES " +
                        "WHERE " +
                        " table_schema =(SELECT DATABASE()) " +
                        "AND table_name = ? ", tableNames[i]);
                List<Map> columns = DBHelper.query(" " +
                        "SELECT " +
                        " column_name columnName , " +
                        " data_type dataType , " +
                        " column_comment columnComment , " +
                        " column_key columnKey , " +
                        " is_nullable columnNullable , " +
                        " column_type columnType , " +
                        " column_comment columnComment , " +
                        " extra " +
                        "FROM " +
                        " information_schema. COLUMNS " +
                        "WHERE " +
                        " table_name = ? " +
                        "AND table_schema =(SELECT DATABASE()) " +
                        "ORDER BY " +
                        " ordinal_position", tableNames[i]);
                TableInfo tableInfo = new TableInfo();
                String tableName = String.valueOf(table.get("tableName"));
                tableInfo.setTableName(tableName.toUpperCase());
                tableInfo.setDescription(String.valueOf(table.get("tableComment")));
                tableInfo.setTableDbName(tableName);
                String moduleName = Config.MODULE_NAME;
                if (StringUtils.isEmpty(moduleName)) {
                    moduleName = tableName.replace(Config.DB_PREFIX, "")
                            .substring(0, tableName.indexOf("_"));
                }
                tableInfo.setModuleName(moduleName.toLowerCase());

                //
                List<ColumnInfo> columnList = new ArrayList<ColumnInfo>();
                for (Map col : columns) {
                    ColumnInfo column = new ColumnInfo();
                    String columnName = String.valueOf(col.get("columnName"));
                    String columnKey = String.valueOf(col.get("columnKey"));
                    String columnComment = String.valueOf(col.get("columnComment"));
                    String columnType = String.valueOf(col.get("columnType"));
                    String columnNullable = String.valueOf(col.get("columnNullable")).toUpperCase();
                    String desc = StringUtils.isEmpty(columnComment) || "null".equals(columnComment) ? String.format("%s.%s", tableName, columnName) : columnComment;
                    column.setName(columnName);
                    column.setKey(StringUtils.isEmpty(columnKey) && !"null".equals(columnKey) ? false : true);
                    column.setNotNull("NO".equals(columnNullable) ? true : false);
                    column.setType(columnType);
                    column.setDescription(desc);
                    column.setExample("example:");
                    columnList.add(column);
                }
                tableInfo.setColumns(columnList);
                tableList.add(tableInfo);
            }
        } else if (DbTypeEnum.POSTGRESQL == dbTypeEnum) {
            for (int i = 0; i < tableNames.length; i++) {
                Map table = DBHelper.queryOne("SELECT " +
                        " schemaname AS schemaName, " +
                        " tablename AS tableName, " +
                        " obj_description(relfilenode, 'pg_class') AS tableComment " +
                        "FROM " +
                        " pg_tables A, " +
                        " pg_class b " +
                        "WHERE " +
                        " A .tablename = b.relname " +
                        "AND tablename NOT LIKE 'pg%' " +
                        "AND tablename NOT LIKE 'sql_%' " +
                        "AND tablename = ? " +
                        "ORDER BY " +
                        " tablename;", tableNames[i]);
                List<Map> columns = DBHelper.query("SELECT " +
                        " A .attname AS columnName, " +
                        " format_type(A .atttypid, A .atttypmod) AS columnType, " +
                        " A .attnotnull AS columnNotNull, " +
                        " col_description(A .attrelid, A .attnum) AS columnComment, " +
                        " ( " +
                        "  SELECT " +
                        "   pg_constraint.conname AS pk_name " +
                        "  FROM " +
                        "   pg_constraint " +
                        "  INNER JOIN pg_class ON pg_constraint.conrelid = pg_class.oid " +
                        "  INNER JOIN pg_attribute ON pg_attribute.attrelid = pg_class.oid " +
                        "  AND pg_attribute.attnum = pg_constraint.conkey [ 1 ] " +
                        "  WHERE " +
                        "   pg_class.relname = C .relname " +
                        "  AND pg_constraint.contype = 'p' " +
                        "  AND pg_attribute.attname = A .attname " +
                        " ) AS columnKey " +
                        "FROM " +
                        " pg_class AS C, " +
                        " pg_attribute AS A " +
                        "WHERE " +
                        " C .relname = ? " +
                        "AND A .attname NOT LIKE '%pg.%' " +
                        "AND A .attrelid = C .oid " +
                        "AND A .attnum > 0", tableNames[i]);
                TableInfo tableInfo = new TableInfo();
                String tableName = String.valueOf(table.get("tablename"));
                tableInfo.setTableName(tableName.toUpperCase());
                tableInfo.setDescription(String.valueOf(table.get("tablecomment")));
                tableInfo.setTableDbName(tableName);
                String moduleName = Config.MODULE_NAME;
                if (StringUtils.isEmpty(moduleName)) {
                    moduleName = tableName.replace(Config.DB_PREFIX, "")
                            .substring(0, tableName.indexOf("_"));
                }
                tableInfo.setModuleName(moduleName.toLowerCase());

                //
                List<ColumnInfo> columnList = new ArrayList<ColumnInfo>();
                for (Map col : columns) {
                    ColumnInfo column = new ColumnInfo();
                    String columnName = String.valueOf(col.get("columnname"));
                    String columnKey = String.valueOf(col.get("columnkey"));
                    String columnComment = String.valueOf(col.get("columncomment"));
                    String columnType = String.valueOf(col.get("columntype"));
                    String columnNotNull = String.valueOf(col.get("columnnotnull")).toUpperCase();
                    String desc = StringUtils.isEmpty(columnComment) || "null".equals(columnComment) ? String.format("%s.%s", tableName, columnName) : columnComment;

                    column.setName(columnName);
                    column.setKey(StringUtils.isEmpty(columnKey) && !"null".equals(columnKey) ? false : true);
                    column.setNotNull("t".equals(columnNotNull) ? true : false);
                    column.setType(columnType);
                    column.setDescription(desc);
                    column.setExample("example:");
                    columnList.add(column);
                }
                tableInfo.setColumns(columnList);
                tableList.add(tableInfo);
            }
        }
        return tableList;
    }

    public static void main(String[] args) throws IOException {
        String separator = File.separator;
        //File file = new File(System.getProperty("user.dir") + Config.DB_DEFINE_FILE.replaceAll("/", File.separator));
        //List<TableInfo> list = CodeGeneratorUtils.readXlsx(file);
        //System.out.println(list);
        //
        List<TableInfo> list2 = CodeGeneratorUtils.readDb(new String[]{"stdom_order"}, DbTypeEnum.POSTGRESQL);
        System.out.println(list2);
    }
}
