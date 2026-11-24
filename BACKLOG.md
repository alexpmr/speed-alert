# Backlog — PT2VHF Speed Alert

Itens registrados a partir de relatos de testadores e do mantenedor. Não implementar até o pedido de versão nova
(regras do mantenedor em `alexpmr/PT2VHF-Site/CLAUDE.md`).

## Pendentes

### Limite errado na Av. das Nações, em frente à PGR (Brasília) — 80 km/h no app, 60 km/h na via (24/11/2026)

- **Relato:** Leandro Gonçalves (testador, e-mail de 23/11/2026): o limite do trecho da Avenida das Nações em frente à
  Procuradoria-Geral da República foi reduzido para 60 km/h há alguns meses, mas o app ainda mostra 80 km/h.
- **Causa provável:** o `maxspeed` desse trecho no OpenStreetMap ainda está em 80, e a base offline (`data/osm-db.lock`,
  workflow `osm-database.yml`) vem do OSM.
- **Resolver:** conferir o trecho no OSM; corrigir o `maxspeed` para 60 (com fonte) ou, se a correção no OSM não for
  possível a tempo, prever uma lista local de correções aplicada na geração da base. Regerar a base e validar o ponto.

### Via não reconhecida no Setor Bancário Sul, pista em frente à matriz da Caixa (Brasília) (24/11/2026)

- **Relato:** Leandro Gonçalves (mesmo e-mail): ao entrar no Setor Bancário Sul, na pista em frente à matriz da Caixa
  Econômica Federal, o app não reconheceu a via.
- **Causa provável:** via ausente na base offline (tipo de via filtrado na geração da base) ou sem `maxspeed` no OSM.
- **Resolver:** localizar a via no OSM, ver se ela entra no filtro da base e se tem limite; ajustar o filtro ou os dados,
  regerar a base e validar o ponto.

## Concluídos

Nenhum ainda.
