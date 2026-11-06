# JavaBank

API REST de demonstração para cadastro de contas, autenticação e transferências entre usuários. O projeto não é um sistema bancário certificado para movimentação de dinheiro real.

## Tecnologias

- Java 21 e Spring Boot 3.5
- Spring MVC, Spring Security 6, JWT e BCrypt
- Spring Data JPA, PostgreSQL e Flyway
- H2 para testes, JUnit 5, Mockito e JaCoCo
- springdoc-openapi / Swagger UI, Spring Boot Actuator e Docker Compose

## Arquitetura

`controllers` recebe e valida as requisições; `services` aplica as regras de negócio; `repositories` persiste entidades JPA; `dto` define os contratos públicos e `mappers` converte entidades em respostas. As entidades nunca são serializadas diretamente.

Transferências são executadas em uma transação, com bloqueio pessimista das duas contas em ordem determinística e versão otimista. Somente contas COMMON podem enviar valores, o valor mínimo é R$ 1,00 e o remetente vem da identidade autenticada. O histórico é visível apenas aos participantes. Novos cadastros começam com saldo zero; créditos iniciais são feitos pelo endpoint administrativo e registrados no histórico.

## Executar com Docker

Com Docker Desktop e Docker Compose instalados:

```shell
docker compose up --build
```

O Compose aguarda o PostgreSQL ficar saudável, aplica as migrations e publica a API em `http://localhost:8080`. O JWT de desenvolvimento é gerado aleatoriamente quando `JWT_SECRET` não é informado; os tokens deixam de valer após recriar o contêiner. O PostgreSQL usa autenticação `trust` somente na rede privada deste Compose de desenvolvimento e não publica sua porta no host. **Não use essa configuração em produção.**

Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Para habilitar créditos administrativos, copie `.env.example` para `.env` e configure `BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_PASSWORD` (mínimo de 12 caracteres) antes de iniciar. Sem essas variáveis, a API inicia sem uma conta ADMIN e o endpoint de crédito fica indisponível. O arquivo `.env` é ignorado pelo Git. Em produção, configure também uma chave JWT Base64 aleatória persistente (`JWT_SECRET` com pelo menos 32 bytes decodificados), credenciais PostgreSQL e os endereços externos; selecione `SPRING_PROFILES_ACTIVE=prod`.

## Autenticar e testar

1. Registre duas contas COMMON em `POST /auth/register`, informando nome, e-mail, senha e CPF válido. O cadastro não aceita saldo inicial.
2. Se configurou o ADMIN, faça login em `POST /auth/login` com as credenciais de bootstrap e copie `accessToken`.
3. Use o token ADMIN em `POST /admin/users/{id}/credits` para creditar a conta remetente. O crédito é registrado no histórico como `ADMIN_CREDIT`.
4. Faça login com a conta remetente, use seu token no botão **Authorize** do Swagger e envie `POST /transactions` com `recipientId` e `amount`.
5. Consulte `GET /transactions` ou `GET /transactions/{id}` com o token de qualquer participante.

Os endpoints de registro/login e a documentação OpenAPI são públicos. `GET /users` e `/admin/**` exigem ADMIN; usuários autenticados só veem a própria conta e transações das quais participam. A autorização de transferências consulta o serviço externo definido por `AUTHORIZATION_URL` e falha de forma segura quando ele está indisponível. Notificações usam `NOTIFICATION_URL` depois do commit: falhas são registradas sem desfazer uma transferência concluída.

Exemplo de registro:

```bash
curl -i http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"completeName":"Maria Silva","email":"maria@example.com","password":"UmaSenhaForte123!","document":"52998224725"}'
```

Exemplo de login:

```bash
curl -s http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@example.com","password":"UmaSenhaForte123!"}'
```

Exemplo de transferência (substitua o token e o ID do destinatário):

```bash
curl -i http://localhost:8080/transactions \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"recipientId":2,"amount":25.50}'
```

Os endpoints retornam erros de domínio no formato RFC 7807. Contas com CNPJ são classificadas como MERCHANT e não podem iniciar transferências.

## Executar localmente sem Docker

É necessário Java 21, Maven 3.9+ e PostgreSQL disponível. Crie o banco e configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET` no ambiente. Gere uma chave local com OpenSSL, por exemplo `openssl rand -base64 32`, e execute:

```shell
mvn clean verify
mvn spring-boot:run
```

O perfil padrão é `dev`; `test` usa H2 e um schema de teste validado pelo Hibernate. `prod` exige variáveis de conexão PostgreSQL. `ddl-auto` permanece em `validate`; mudanças de esquema são feitas por migrations Flyway.

## Variáveis de ambiente

| Variável | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil Spring (`dev`, `test` ou `prod`) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão PostgreSQL |
| `JWT_SECRET` | Chave Base64 com pelo menos 32 bytes decodificados |
| `JWT_EXPIRATION` | Duração do token, por exemplo `PT15M` |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Cria a conta ADMIN inicial quando ambas estão configuradas |
| `AUTHORIZATION_URL`, `NOTIFICATION_URL` | Endpoints externos; possuem valores de demonstração no perfil `dev` |
| `INTEGRATION_CONNECT_TIMEOUT`, `INTEGRATION_READ_TIMEOUT` | Limites de conexão/leitura das chamadas externas |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula |
| `SERVER_PORT` | Porta HTTP da aplicação (padrão `8080`) |

## Testes

```shell
mvn clean verify
```

Os testes usam H2 e cobrem validação de documentos, registro/login, respostas 401/403, autorização por propriedade, créditos administrativos, transferências, saldo insuficiente, tipo de conta e duas transferências concorrentes disputando o mesmo saldo. O relatório JaCoCo é gerado em `target/site/jacoco/`.

O arquivo [`requests.http`](requests.http) contém as mesmas operações de exemplo para IDEs compatíveis com HTTP Client; substitua os tokens e IDs pelos retornados na execução.

## Migrations e dados legados

As migrations V1–V4 são preservadas; V5 migra o esquema para saldos decimais, documento único e campos de autenticação/histórico. V5 copia os valores da coluna JPA antiga `transferred_value` quando ela existe; registros sem valor persistido permanecem com `amount: null`. Contas legadas recebem um hash de senha inutilizável porque o sistema antigo não armazenava senhas; elas precisam ser recuperadas por um processo administrativo seguro antes de autenticar.
