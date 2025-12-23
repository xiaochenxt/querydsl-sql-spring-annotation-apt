package io.github.xiaochenxt.processor;

import org.springframework.data.util.ParsingUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表字段常量生成，完全兼容querydsl-sql生成的格式
 *
 * @author xiaochen
 */
public class QuerydslSqlSpringAnnotationProcessor extends AbstractProcessor {

    private Map<String, String> options;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        options = processingEnv.getOptions();
    }

    /**
     * 支持的注解类型
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add("org.springframework.data.relational.core.mapping.Table");
        return annotations;
    }

    /**
     * 支持最新版本
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> elements = new HashSet<>();
        for (TypeElement annotation : annotations) {
            elements.addAll(roundEnv.getElementsAnnotatedWith(annotation));
        }
        for (Element element : elements) {
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement typeElement = (TypeElement) element;
                generateFieldConstants(typeElement);
            }
        }
        return false;
    }

    /**
     * 生成字段常量信息
     */
    private void generateFieldConstants(TypeElement typeElement) {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName().toString();
        String qClassName = "Q" + typeElement.getSimpleName();
        try {
            Filer filer = processingEnv.getFiler();
            JavaFileObject sourceFile = filer.createSourceFile(packageName + "." + qClassName);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write("package " + packageName + ";\n\n");
                writer.write("import static com.querydsl.core.types.PathMetadataFactory.*;\n" +
                        "import com.querydsl.core.types.dsl.*;\n" +
                        "import com.querydsl.core.types.PathMetadata;\n" +
                        "import javax.annotation.processing.Generated;\n" +
                        "import com.querydsl.core.types.Path;\n" +
                        "import com.querydsl.sql.ColumnMetadata;\n" +
                        "import java.sql.Types;\n\n");
                writer.write("/**\n * 根据" + typeElement.getQualifiedName().toString() + "自动生成\n * @author 小郴\n * @since " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n */\n");
                writer.write("@Generated(value =\"" + this.getClass().getName() + "\", date =\"" +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) +
                        "\", comments =\"根据" + typeElement.getQualifiedName().toString() + "自动生成\")\n");
                writer.write("public class " + qClassName + " extends com.querydsl.sql.RelationalPathBase<" + qClassName + "> {\n\n");
                // 生成字段常量
                String table = getTableName(typeElement);
                String schema = getTableSchema(typeElement);
                String classObjectName = className.substring(0, 1).toLowerCase() + (className.length() == 1 ? "" : className.substring(1));
                writer.write("    public static final " + qClassName + " " + classObjectName + " = new " + qClassName + "(\"" + table + "\");\n\n");
                List<ColumnInfo> columns = new ArrayList<>();
                processFields(qClassName, className, typeElement, writer, table, columns);
                writer.write("    public " + qClassName + "(String variable) {\n");
                writer.write("        super(" + qClassName + ".class, forVariable(variable), \""+schema+"\", \"" + table + "\");\n");
                writer.write("        addMetadata();\n    }\n\n");
                writer.write("    public " + qClassName + "(String variable, String schema, String table) {\n");
                writer.write("        super(" + qClassName + ".class, forVariable(variable), schema, table);\n");
                writer.write("        addMetadata();\n    }\n\n");
                writer.write("    public " + qClassName + "(Path<? extends " + qClassName + "> path) {\n");
                writer.write("        super(path.getType(), path.getMetadata(), \""+schema+"\", \"" + table + "\");\n");
                writer.write("        addMetadata();\n    }\n\n");
                writer.write("    public " + qClassName + "(PathMetadata metadata) {\n");
                writer.write("        super(" + qClassName + ".class, metadata, \""+schema+"\", \"" + table + "\");\n");
                writer.write("        addMetadata();\n    }\n\n");
                writer.write("    public void addMetadata() {\n");
                for (int i = 0; i < columns.size(); i++) {
                    ColumnInfo columnInfo = columns.get(i);
                    boolean nullable = columnInfo.isNullable();
                    String javaType = columnInfo.getJavaType();
                    String columnType;
                    if (javaType.equals("java.lang.String")) {
                        if (columnInfo.isJson()) {
                            columnType = "ofType(Types.VARCHAR).withSize(" + Integer.MAX_VALUE + ")";
                        }else {
                            columnType = "ofType(Types.VARCHAR).withSize(" + columnInfo.getLength() + ")";
                        }
                    } else if (javaType.equals("java.lang.Integer")) {
                        if (columnInfo.getColumnDefinition().contains("tinyint")) {
                            columnType = "ofType(Types.TINYINT).withSize(3)";
                        } else if (columnInfo.getColumnDefinition().contains("smallint")) {
                            columnType = "ofType(Types.SMALLINT).withSize(3)";
                        } else {
                            columnType = "ofType(Types.INTEGER).withSize(10)";
                        }
                    } else if (javaType.equals("java.lang.Long")) {
                        columnType = "ofType(Types.BIGINT).withSize(19)";
                    } else if (javaType.equals("java.util.Date")) {
                        columnType = "ofType(Types.TIMESTAMP).withSize(19)";
                    } else if (javaType.equals("java.time.LocalDateTime")) {
                        columnType = "ofType(Types.TIMESTAMP).withSize(29).withDigits(6)";
                    } else if (javaType.equals("java.time.LocalDate")) {
                        columnType = "ofType(Types.DATE).withSize(10)";
                    } else if (javaType.equals("java.time.LocalTime")) {
                        columnType = "ofType(Types.TIME).withSize(10)";
                    } else if (javaType.equals("java.sql.Timestamp")) {
                        columnType = "ofType(Types.TIMESTAMP).withSize(19)";
                    } else if (javaType.equals("java.math.BigDecimal")) {
                        columnType = "ofType(Types.NUMERIC).withSize(" + columnInfo.getPrecision() + ").withDigits(" + columnInfo.getScale() + ")";
                    } else if (javaType.equals("java.lang.Float")) {
                        columnType = "ofType(Types.FLOAT).withSize(5)";
                    } else if (javaType.equals("java.lang.Double")) {
                        columnType = "ofType(Types.DOUBLE).withSize(5)";
                    } else if (javaType.equals("java.lang.Byte")) {
                        columnType = "ofType(Types.CHAR).withSize(1)";
                    } else if (javaType.equals("java.lang.Short")) {
                        columnType = "ofType(Types.NUMERIC).withSize(5)";
                    } else if (javaType.equals("java.lang.Boolean")) {
                        columnType = "ofType(Types.BIT).withSize(1)";
                    } else {
                        if (columnInfo.isJson()) {
                            columnType = "ofType(Types.OTHER).withSize("+ Integer.MAX_VALUE +")";
                        } else {
                            columnType = "ofType(Types.VARCHAR).withSize("+ columnInfo.getLength() +")";
                        }
                    }
                    if (!nullable) columnType += ".notNull()";
                    int index = i + 1;
                    writer.write("        addMetadata(" + columnInfo.getJavaField() + ", ColumnMetadata.named(\"" + columnInfo.getColumn() + "\").withIndex(" + index + ")." + columnType + ");\n");
                }
                writer.write("    }\n");
                writer.write("}\n");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    /**
     * 处理字段
     */
    private void processFields(String qClassName, String className, TypeElement typeElement, Writer writer, String table, List<ColumnInfo> columns) throws IOException {
        // 先生成父类字段
        TypeMirror superClassType = typeElement.getSuperclass();
        if (superClassType.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredSuperClass = (DeclaredType) superClassType;
            Element superClassElement = declaredSuperClass.asElement();
            if (superClassElement instanceof TypeElement) {
                TypeElement superTypeElement = (TypeElement) superClassElement;
                if (!superTypeElement.getQualifiedName().toString().equals("java.lang.Object")) {
                    processFields(qClassName, className, superTypeElement, writer, table, columns);
                }
            }
        }
        // 生成当前类字段
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                String fieldName = enclosedElement.getSimpleName().toString();
                if (!shouldBeIgnored(enclosedElement)) {
                    String constantName = constantName(fieldName);
                    String column = getColumnValue(enclosedElement).orElseGet(() -> constantValue(fieldName));
                    if (!columns.stream().map(ColumnInfo::getColumn).collect(Collectors.toList()).contains(column)) {
                        ColumnInfo columnInfo = new ColumnInfo();
                        String javaType = enclosedElement.asType().toString();
                        columnInfo.setColumn(column);
                        columnInfo.setJavaType(javaType);
                        columnInfo.setJavaField(constantName);
                        columns.add(columnInfo);
                        writerColumn(enclosedElement,writer,javaType,constantName,columnInfo);
                        if (hasAnnotation(enclosedElement, "org.springframework.data.annotation.Id")) {
                            writer.write("    " + "/**\n     *" + " 数据库主键\n" + "     */\n");
                            writer.write("    public final com.querydsl.sql.PrimaryKey<" + qClassName + "> primaryKey = createPrimaryKey(" + constantName + ");\n\n");
                        }
                    }
                }
            }
        }
    }

    public void writerColumn(Element enclosedElement,Writer writer,String javaType,String constantName,ColumnInfo columnInfo) throws IOException {
        String javadoc = getJavadoc(enclosedElement);
        if (!Objects.isNull(javadoc) && !javadoc.isEmpty()) {
            writer.write("    " + "/**\n     *" + getJavadoc(enclosedElement) + "     */\n");
        }
        columnInfo.setNullable(getColumnNullable(enclosedElement));
        columnInfo.setJson(isJson(enclosedElement));
        if (javaType.equals("java.lang.String")) {
            writer.write("    public final StringPath " + constantName + " = createString(\"" + constantName + "\");\n\n");
            columnInfo.setLength(getColumnLength(enclosedElement));
        } else if (javaType.equals("java.lang.Integer")) {
            writer.write("    public final NumberPath<Integer> " + constantName + " = createNumber(\"" + constantName + "\", Integer.class);\n\n");
            columnInfo.setColumnDefinition(getColumnColumnDefinition(enclosedElement));
        } else if (javaType.equals("java.lang.Long")) {
            writer.write("    public final NumberPath<Long> " + constantName + " = createNumber(\"" + constantName + "\", Long.class);\n\n");
        } else if (javaType.equals("java.util.Date")) {
            writer.write("    public final DateTimePath<java.util.Date> " + constantName + " = createDateTime(\"" + constantName + "\", java.util.Date.class);\n\n");
        } else if (javaType.equals("java.time.LocalDateTime")) {
            writer.write("    public final DateTimePath<java.time.LocalDateTime> " + constantName + " = createDateTime(\"" + constantName + "\", java.time.LocalDateTime.class);\n\n");
        } else if (javaType.equals("java.time.LocalDate")) {
            writer.write("    public final DateTimePath<java.time.LocalDate> " + constantName + " = createDateTime(\"" + constantName + "\", java.time.LocalDate.class);\n\n");
        } else if (javaType.equals("java.time.LocalTime")) {
            writer.write("    public final DateTimePath<java.time.LocalTime> " + constantName + " = createDateTime(\"" + constantName + "\", java.time.LocalTime.class);\n\n");
        } else if (javaType.equals("java.sql.Timestamp")) {
            writer.write("    public final DateTimePath<java.sql.Timestamp> " + constantName + " = createDateTime(\"" + constantName + "\", java.sql.Timestamp.class);\n\n");
        } else if (javaType.equals("java.math.BigDecimal")) {
            writer.write("    public final NumberPath<java.math.BigDecimal> " + constantName + " = createNumber(\"" + constantName + "\", java.math.BigDecimal.class);\n\n");
            columnInfo.setPrecision(getColumnPrecision(enclosedElement));
            columnInfo.setScale(getColumnScale(enclosedElement));
        } else if (javaType.equals("java.lang.Float")) {
            writer.write("    public final NumberPath<Float> " + constantName + " = createNumber(\"" + constantName + "\", Float.class);\n\n");
        } else if (javaType.equals("java.lang.Double")) {
            writer.write("    public final NumberPath<Double> " + constantName + " = createNumber(\"" + constantName + "\", Double.class);\n\n");
        } else if (javaType.equals("java.lang.Byte")) {
            writer.write("    public final NumberPath<Byte> " + constantName + " = createNumber(\"" + constantName + "\", Byte.class);\n\n");
        } else if (javaType.equals("java.lang.Short")) {
            writer.write("    public final NumberPath<Short> " + constantName + " = createNumber(\"" + constantName + "\", Short.class);\n\n");
        } else if (javaType.equals("java.lang.Boolean")) {
            writer.write("    public final BooleanPath " + constantName + " = createBoolean(\"" + constantName + "\");\n\n");
        } else {
            writer.write("    public final SimplePath<" + javaType + "> " + constantName + " = createSimple(\"" + constantName + "\", " + javaType + ".class);\n\n");
        }
    }

    /**
     * 生成字段常量名
     *
     * @param fieldName 字段名
     * @return 常量名
     */
    private String constantName(String fieldName) {
        return fieldName;
    }

    /**
     * 解析元素上指定的字段常量值
     *
     * @return 字段名
     */
    private Optional<String> getColumnValue(Element fieldElement) {
        Optional<? extends AnnotationMirror> annotationMirrorOptional = getAnnotationMirror(fieldElement, "org.springframework.data.relational.core.mapping.Column");
        if (annotationMirrorOptional.isPresent()) {
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirrorOptional.get().getElementValues();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                String key = entry.getKey().getSimpleName().toString();
                if ("value".equals(key)) {
                    String value = entry.getValue().getValue().toString();
                    if (!value.isEmpty()) {
                        return Optional.of(value);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 解析元素上指定的dll的sql片段
     *
     * @return 字段名
     */
    private String getColumnColumnDefinition(Element fieldElement) {
        return "";
    }

    /**
     * 解析元素上指定的字段长度
     *
     * @return 字段长度
     */
    private int getColumnLength(Element fieldElement) {
        return 0;
    }

    /**
     * 解析元素上指定的字段是否可为空
     *
     * @return 是否可为空
     */
    private boolean getColumnNullable(Element fieldElement) {
        return true;
    }

    /**
     * 解析元素上指定的字段精度
     *
     * @return 字段精度
     */
    private int getColumnPrecision(Element fieldElement) {
        return 0;
    }

    /**
     * 解析元素上指定的字段decimal小数位数
     *
     * @return 小数位数
     */
    private int getColumnScale(Element fieldElement) {
        return 2;
    }

    /**
     * 生成字段常量值
     *
     * @param fieldName 字段名
     * @return 字段名
     */
    private String constantValue(String fieldName) {
        return ParsingUtils.reconcatenateCamelCase(fieldName,"_");
    }

    private String getTableName(TypeElement element) {
        Optional<? extends AnnotationMirror> annotationMirrorOptional = getAnnotationMirror(element, "org.springframework.data.relational.core.mapping.Table");
        if (annotationMirrorOptional.isPresent()) {
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirrorOptional.get().getElementValues();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                if ("value".equals(entry.getKey().getSimpleName().toString())) {
                    return entry.getValue().getValue().toString();
                }
                if ("name".equals(entry.getKey().getSimpleName().toString())) {
                    return entry.getValue().getValue().toString();
                }
            }
        }
        return element.getSimpleName().toString();
    }

    private String getTableSchema(TypeElement element) {
        Optional<? extends AnnotationMirror> annotationMirrorOptional = getAnnotationMirror(element, "org.springframework.data.relational.core.mapping.Table");
        if (annotationMirrorOptional.isPresent()) {
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirrorOptional.get().getElementValues();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                if ("schema".equals(entry.getKey().getSimpleName().toString())) {
                    return entry.getValue().getValue().toString().isEmpty() ? "public" : entry.getValue().getValue().toString();
                }
            }
        }
        return "public";
    }

    private boolean isJson(Element fieldElement) {
        Optional<? extends AnnotationMirror> annotationMirrorOptional = getAnnotationMirror(fieldElement, "org.hibernate.annotations.JdbcTypeCode");
        if (annotationMirrorOptional.isPresent()) {
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirrorOptional.get().getElementValues();
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                if ("value".equals(entry.getKey().getSimpleName().toString())) {
                    return "3001".equals(entry.getValue().getValue().toString());
                }
            }
        }
        return false;
    }

    /**
     * 获取指定的注解元素
     */
    private Optional<? extends AnnotationMirror> getAnnotationMirror(Element element, String annotationName) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotationName)) {
                return Optional.of(annotationMirror);
            }
        }
        return Optional.empty();
    }

    /**
     * 应该忽略的元素
     */
    private boolean shouldBeIgnored(Element fieldElement) {
        return fieldElement.getModifiers().contains(Modifier.STATIC) ||
                fieldElement.getModifiers().contains(Modifier.FINAL) ||
                hasAnnotation(fieldElement, "org.springframework.data.annotation.Transient");
    }

    /**
     * 是否包含某个注解
     */
    private boolean hasAnnotation(Element fieldElement, String annotation) {
        for (AnnotationMirror annotationMirror : fieldElement.getAnnotationMirrors()) {
            DeclaredType annotationType = annotationMirror.getAnnotationType();
            String annotationName = annotationType.toString();
            if (annotationName.equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文档注释
     *
     * @return 文档注释
     */
    private String getJavadoc(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        return elementUtils.getDocComment(element);
    }

    public static class ColumnInfo {

        /**
         * java字段类型
         */
        private String javaType;

        /**
         * java字段名
         */
        private String javaField;

        /**
         * 数据库字段名
         */
        private String column;

        /**
         * 字段长度
         */
        private Integer length;

        private boolean nullable;

        private boolean isJson;

        /**
         * 生成的SQL以创建推断类型的列
         */
        private String columnDefinition;

        private Integer precision;

        private Integer scale;

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        public String getJavaField() {
            return javaField;
        }

        public void setJavaField(String javaField) {
            this.javaField = javaField;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }

        public String getColumnDefinition() {
            return columnDefinition;
        }

        public void setColumnDefinition(String columnDefinition) {
            this.columnDefinition = columnDefinition;
        }

        public Integer getPrecision() {
            return precision != null && precision != 0 ? precision : 0;
        }

        public void setPrecision(Integer precision) {
            this.precision = precision;
        }

        public Integer getScale() {
            return scale != null && scale != 0 ? scale : 2;
        }

        public void setScale(Integer scale) {
            this.scale = scale;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }

        public boolean isJson() {
            return isJson;
        }

        public void setJson(boolean json) {
            isJson = json;
        }
    }

}

