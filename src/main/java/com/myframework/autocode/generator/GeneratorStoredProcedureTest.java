package com.myframework.autocode.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myframework.autocode.entity.ColumnInfo;
import com.myframework.autocode.entity.TableInfo;
import com.myframework.autocode.util.CodeGeneratorUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class GeneratorStoredProcedureTest
{

	public static void main(String[] args) throws Exception
	{
		File xlsxFile = new File(System.getProperty("user.dir") + CodeGeneratorUtils.DB_DEFINE_FILE);
		List<TableInfo> tableInfoList = CodeGeneratorUtils.readXlsx(xlsxFile);

		Configuration cfg = new Configuration();
		cfg.setClassForTemplateLoading(GeneratorStoredProcedureTest.class, CodeGeneratorUtils.AUTOCODE_PATH);

		String basePath = System.getProperty("user.dir") + CodeGeneratorUtils.OUTPUT_PATH;

		Map<String, Object> dataMap = new HashMap<String, Object>();
		List<Map<String, Object>> pros = new ArrayList<Map<String, Object>>();
		for (TableInfo tableInfo : tableInfoList)
		{
			Map<String, Object> proMap = new HashMap<String, Object>();
			proMap.put("tableName", tableInfo.getTableName());
			proMap.put("tableDbName", tableInfo.getTableDbName());

			List<Map<String, Object>> columnPros = new ArrayList<Map<String, Object>>();
			List<ColumnInfo> columnInfoList = tableInfo.getColumns();
			for (ColumnInfo columnInfo : columnInfoList)
			{
				Map<String, Object> columnMap = new HashMap<String, Object>();
				columnMap.put("columnName", columnInfo.getName());
				columnMap.put("description", columnInfo.getDescription());
				columnMap.put("example", columnInfo.getExample());
				columnPros.add(columnMap);
			}
			proMap.put("columnList", columnPros);

			pros.add(proMap);
		}
		dataMap.put("properties", pros);

		File pathFile = new File(basePath + "StoredProcedure.txt");
		pathFile.getParentFile().mkdirs();
		FileOutputStream fos = new FileOutputStream(pathFile);
		Writer writer = new OutputStreamWriter(fos, "UTF-8");
		Template t = cfg.getTemplate("StoredProcedureGeneratorTemplate");
		t.process(dataMap, writer);
		fos.flush();
		fos.close();
		writer.close();

		System.out.println("导出成功：" + pathFile.getAbsolutePath());
	}

}