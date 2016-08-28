# IntelliAstrix
IntelliJ Plugin for [Astrix](https://github.com/AvanzaBank/astrix)

## Features
* Validate that beans retrieved from Astrix context are declared in the current modules runtime classpath
* Link to bean declaration
* Link from bean declaration to bean retrievals
* Indicate whether retrieved bean is a Service or a Library

## In progress
* Tests...
* Build with Gradle and [gradle-intellij-plugin](https://github.com/JetBrains/gradle-intellij-plugin)

## Planned Features
* Consider AstrixDynamicQualifier
* Validate parameters to bean factory methods
* Suggest only valid bean types
* Suggest only valid qualifiers

## Ideas
* Extend Spring plugin with support for Spring beans automatically added by Astrix
