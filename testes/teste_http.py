from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.request import build_opener, HTTPCookieProcessor
from http.cookiejar import CookieJar
from urllib.parse import urlencode
from datetime import date, timedelta
import re
import json
from pathlib import Path

base = 'http://127.0.0.1:8086'
resultados = []
cookies = CookieJar()
cliente = build_opener(HTTPCookieProcessor(cookies))

def pedir(caminho, dados=None, metodo=None, origem=None):
    corpo = urlencode(dados).encode() if isinstance(dados, dict) else dados
    pedido = Request(base + caminho, data=corpo, method=metodo, headers={'Origin': base if origem is None else origem} if corpo is not None else {})
    try:
        resposta = cliente.open(pedido, timeout=15)
    except HTTPError as erro:
        resposta = erro
    with resposta:
        return resposta.status, resposta.read().decode('utf-8'), resposta.geturl(), resposta.headers

def conferir(nome, condicao):
    assert condicao, nome
    resultados.append(nome)

status, pagina, _, headers = pedir('/')
conferir('Projeto extraído abre com o inicializador', status == 200 and 'FL Barbearia' in pagina)
conferir('Início oferece acesso direto à reserva sem formulários misturados', '/consultar' in pagina and '/agendar' in pagina and '<form' not in pagina)
status, pagina, _, _ = pedir('/agendar')
conferir('Primeira etapa pede apenas serviço, profissional e dia', status == 200 and 'Escolher horário' in pagina and 'name="nome"' not in pagina)
status, pagina, _, _ = pedir('/agendar?etapa=horarios')
token = re.search(r'name="token" value="([a-f0-9]+)"', pagina)[1]
conferir('Páginas não carregam serviços externos', not re.search(r'(?:src|href)="https?://', pagina.replace('href="https://github.com/Felipe-Landgraf"', '')))
conferir('Cabeçalhos limitam os recursos ao próprio site', "default-src 'self'" in headers.get('Content-Security-Policy', ''))
conferir('Arquivos do banco e fontes não ficam expostos', pedir('/dados/agenda.mv.db')[0] == 404 and pedir('/src/barbearia/Banco.java')[0] == 404)
conferir('Envio sem proteção do formulário é recusado', pedir('/agendar', {'nome': 'Teste'})[0] == 403)
conferir('Navegador incorporado também precisa do token', pedir('/agendar', {'nome': 'Teste'}, origem='null')[0] == 403)
conferir('Outro site é recusado mesmo com token válido', pedir('/consultar', {'token': token}, origem='https://outro-site.example')[0] == 403)
conferir('Formulário excessivo é recusado', pedir('/agendar', b'a' * 8193)[0] == 413)
conferir('Método inválido é recusado', pedir('/', metodo='PUT')[0] == 405)
conferir('Data inexistente tem erro legível', pedir('/agendar?data=2026-02-31')[0] == 400)
dia = date.today() + timedelta(days=1)
while dia.weekday() != 0:
    dia += timedelta(days=1)
dados = {'token': token, 'servico': 'combo', 'barbeiro': 'lucas', 'data': str(dia), 'horario': '09:00', 'nome': 'Pessoa de Teste', 'telefone': '19988881234'}
invalidos = dict(dados, nome='<img src=x onerror=alert(1)>', telefone='123')
status, pagina, _, _ = pedir('/agendar', invalidos)
conferir('Erro preserva campos com HTML escapado', status == 400 and 'value="&lt;img src=x onerror=alert(1)&gt;"' in pagina and '<img src=x' not in pagina)
status, pagina, endereco, _ = pedir('/agendar', dados, origem='null')
codigo = re.search(r'id="codigo-reserva">([A-F0-9]{16})', pagina)[1]
conferir('Reserva redireciona para a confirmação', status == 200 and '/reserva?codigo=' in endereco and 'Seu horário está reservado.' in pagina)
conferir('Confirmação não expõe nome nem telefone', dados['nome'] not in pagina and dados['telefone'] not in pagina)
cookie = next(item for item in cookies if item.name == 'fl_reservas')
conferir('Navegador guarda somente o código da reserva', cookie.value == codigo and dados['telefone'] not in cookie.value)
conferir('Cookie de reserva não pode ser lido pelo JavaScript', cookie.has_nonstandard_attr('HttpOnly') and cookie.get_nonstandard_attr('SameSite') == 'Lax')
status, lista, _, _ = pedir('/consultar')
conferir('Meu agendamento mostra a reserva sem pedir código ou telefone', status == 200 and codigo in lista and "class='cartao-reserva'" in lista and '09:00' in lista)
conferir('Página inicial destaca a reserva que já foi feita', 'SEU AGENDAMENTO' in pedir('/')[1] and codigo in pedir('/')[1])
with urlopen(base + '/consultar', timeout=15) as resposta:
    pagina_limpa = resposta.read().decode('utf-8')
conferir('Outro navegador não vê as reservas deste navegador', codigo not in pagina_limpa and 'Nenhum horário marcado por aqui.' in pagina_limpa)
outro = dict(dados, barbeiro='rafael', horario='10:30')
status, segunda, _, _ = pedir('/agendar', outro)
outro_codigo = re.search(r'id="codigo-reserva">([A-F0-9]{16})', segunda)[1]
lista = pedir('/consultar')[1]
conferir('Mais de uma reserva fica acessível na mesma lista', codigo in lista and outro_codigo in lista and lista.count("class='cartao-reserva'") == 2)
status, pagina, _, _ = pedir('/agendar', dados)
conferir('Reenvio não duplica a reserva', status == 400 and 'não está disponível' in pagina)
conferir('Telefone incorreto não permite consultar', pedir('/consultar', {'token': token, 'codigo': codigo, 'telefone': '19988880000'})[0] == 400)
status, pagina, _, _ = pedir('/consultar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone']})
conferir('Consulta válida libera opção de cancelamento', status == 200 and 'form-cancelar' in pagina)
conferir('Cancelamento exige confirmação', pedir('/cancelar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone']})[0] == 400)
conferir('Reserva lembrada ainda exige telefone correto para cancelar', pedir('/cancelar', {'token': token, 'codigo': codigo, 'telefone': '19000000000', 'confirmacao': 'sim'})[0] == 400)
status, pagina, _, _ = pedir('/cancelar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone'], 'confirmacao': 'sim'})
conferir('Cancelamento fica registrado na confirmação', status == 200 and 'Agendamento cancelado.' in pagina)
status, pagina, _, _ = pedir('/agendar?etapa=horarios&servico=combo&barbeiro=lucas&data=' + str(dia))
conferir('O intervalo cancelado fica livre novamente', status == 200 and "value='09:00'" in pagina)
lista = pedir('/consultar')[1]
conferir('Cancelados vão para o histórico sem esconder a reserva ativa', lista.index(outro_codigo) < lista.index('Anteriores e cancelados') < lista.index(codigo))
pedir('/cancelar', {'token': token, 'codigo': outro_codigo, 'telefone': dados['telefone'], 'confirmacao': 'sim'})
domingo = dia + timedelta(days=6)
status, pagina, _, _ = pedir('/agendar?etapa=horarios&data=' + str(domingo))
conferir('Dia sem atendimento explica como escolher outro dia', status == 200 and 'Domingo é dia de descanso' in pagina and 'Alterar serviço ou dia' in pagina and 'id="confirmar" disabled' in pagina)
print(str(len(resultados)) + ' verificações HTTP aprovadas.')
