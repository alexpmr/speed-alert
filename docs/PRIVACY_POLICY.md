# Política de Privacidade — PT2VHF Speed Alert (Android, Google Play)

Última atualização: 18 de novembro de 2026 (versão 1.0.9)

Esta política se aplica ao aplicativo **PT2VHF Speed Alert** (`com.pt2vhf.speedalert`) distribuído pelo Google Play.

## Dados que o aplicativo usa
- **Localização precisa (GPS):** usada no próprio aparelho para calcular a velocidade, identificar a via na base offline OpenStreetMap, mostrar o limite e emitir alertas. Ela não é gravada em histórico. Só sai do aparelho em dois casos, descritos abaixo: o nome ou os limites da área de mapa baixada, que indicam uma região aproximada, e os relatos de erro que você decidir publicar.
- **Configurações do aplicativo** (limite manual, tolerância, volume, tipo de alarme etc.): ficam somente no armazenamento privado do aparelho. A exportação de configurações só ocorre quando você escolhe um arquivo pelo seletor do Android.

## Download da base de mapas por blocos
Os limites de velocidade são baixados em blocos de cerca de 110 km × 110 km ao redor de onde você está. Para isso, o aplicativo pede ao servidor de arquivos (atualmente o GitHub, da Microsoft) apenas o nome do bloco, como "s16w048". Isso revela a região aproximada, mas não a posição exata, a velocidade, o trajeto nem qualquer identificador seu ou do aparelho. Como em qualquer acesso à Internet, o servidor recebe o endereço IP da conexão. Você pode limitar os downloads ao Wi-Fi ou baixar regiões inteiras antes de viajar, em Configurações. Fora das regiões com blocos prontos (Centro-Oeste, Sudeste e Sul do Brasil), a versão do Google Play baixa áreas menores, de cerca de 11 km × 11 km, direto do OpenStreetMap, pelo servidor público Overpass (overpass-api.de, com overpass.kumi.systems como reserva). Nesse caso são enviados ao servidor apenas os quatro limites da área (por exemplo, latitudes −15,9 a −15,8 e longitudes −47,9 a −47,8), o que indica a sua região com precisão de cerca de 11 km, mas não a posição exata, a velocidade, o trajeto nem qualquer identificador. O servidor também recebe o endereço IP da conexão.

## Relatos de erro (opcionais, enviados só com sua confirmação)
Com "Marcar erro", o aplicativo guarda no próprio aparelho a posição, a direção, a via e o limite exibido naquele momento. Nada é enviado nesse passo.

Se você decidir **publicar** um relato, o aplicativo cria uma **nota pública e anônima no OpenStreetMap** (openstreetmap.org, mantido pela OpenStreetMap Foundation). A nota contém:
- a posição marcada;
- o tipo do problema, o valor informado por você e o comentário;
- a via e os valores que o app mostrava;
- a data e as versões do app e da base.

A nota fica visível para qualquer pessoa e é regida pela política de privacidade da OpenStreetMap Foundation (https://osmfoundation.org/wiki/Privacy_Policy). O servidor do OSM recebe também o endereço IP da conexão, como em qualquer acesso à Internet. Não enviamos nome, e-mail, identificador do aparelho nem histórico de trajetos.

## Dados que o aplicativo NÃO coleta
Não há contas, anúncios, rastreamento, analytics nem identificadores. Além das áreas de mapa baixadas e dos relatos que você decidir publicar, o aplicativo não transmite dados.

## Funcionamento em segundo plano
Depois que você aceita o aviso inicial, o aplicativo mantém um serviço em primeiro plano, com notificação permanente, para continuar medindo a velocidade e emitindo alertas quando a tela está bloqueada ou outro aplicativo (como um navegador GPS) está visível. Você pode interromper o serviço a qualquer momento pelo botão **Parar** da notificação.

## Permissões
- Localização precisa e aproximada: medir velocidade e posição na via.
- Serviço em primeiro plano (localização): manter os alertas ativos com a tela bloqueada.
- Notificações: mostrar a notificação do serviço.

## Exclusão de dados
Os dados locais, incluindo os relatos não publicados, são apagados ao excluir o relato, ao desinstalar o aplicativo ou ao limpar os dados nas Configurações do Android. Uma nota já publicada no OpenStreetMap pode ser comentada e fechada por qualquer mapeador. Para pedir a remoção de uma nota, siga o procedimento da OpenStreetMap Foundation.

## Crianças
O aplicativo é destinado a motoristas adultos e não é direcionado a crianças.

## Dados de mapas
Base de vias © colaboradores do OpenStreetMap, licença ODbL 1.0.

## Contato
PT2VHF — https://www.pt2vhf.com
