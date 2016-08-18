# IntelliAstrix
IntelliJ Plugin for [Astrix](https://github.com/AvanzaBank/astrix)

## Features
* Validate that beans retrieved from Astrix context are declared in the current modules runtime classpath
* Link to bean declaration
* Link from bean declaration to bean retrievals (so far only when bean declaration is in current module)

## In progress
* Use of qualifier
* Link from bean declaration in dependency library to any module that has it in its classpath

## Planned Features
- [ ] Suggest only valid bean types
- [ ] Suggest only valid qualifiers
- [ ] Indicate whether retrieved bean is a Service or a Library

## Ideas
* Extend Spring plugin with support for Spring beans automatically added by Astrix
