# querydsl-sql-spring-annotation-apt
JDBC实体类表字段常量生成，代替：http://querydsl.com/static/querydsl/latest/reference/html/ch02.html#jpa_integration
```xml
<plugin>
    <groupId>com.querydsl</groupId>
    <artifactId>querydsl-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>export</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <jdbcDriver>org.postgresql.Driver</jdbcDriver>
        <jdbcUrl>jdbc:postgresql://127.0.0.1:5432/dev</jdbcUrl>
        <jdbcUser>postgres</jdbcUser>
        <jdbcPassword>123456</jdbcPassword>
        <packageName>dto</packageName>
        <targetFolder>${project.basedir}/target/generated-sources/java</targetFolder>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.7</version>
        </dependency>
    </dependencies>
</plugin>
```


引入maven依赖，加SpringData注解即可自动生成
## 开始使用
- Maven:
```xml
<dependency>
    <groupId>io.github.dengchen2020</groupId>
    <artifactId>querydsl-sql-spring-annotation-apt</artifactId>
    <version>0.0.1</version>
    <scope>provided</scope>
</dependency>
```
自动识别@Table,@Column，@Transient等SpringData注解生成字段常量类
