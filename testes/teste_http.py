from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode
from datetime import date, timedelta
import re
import json
from pathlib import Path

base = 'http://127.0.0.1:8086'
resultados = []

def pedir(caminho, dados=None, metodo=None):
    corpo = urlencode(dados).encode() if isinstance(dados, dict) else dados
    pedido = Request(base + caminho, data=corpo, method=metodo)
    try:
        resposta = urlopen(pedido, timeout=15)
    except HTTPError as erro:
        resposta = erro
    with resposta:
        return resposta.status, resposta.read().decode('utf-8'), resposta.geturl(), resposta.headers

def conferir(nome, condicao):
    assert condicao, nome
    resultados.append(nome)

status, pagina, _, headers = pedir('/')
conferir('Projeto extraído abre com o inicializador', status == 200 and 'FL Barbearia' in pagina)
token = re.search(r'name="token" value="([a-f0-9]+)"', pagina)[1]
conferir('Páginas não carregam serviços externos', not re.search(r'(?:src|href)="https?://', pagina.replace('href="https://github.com/Felipe-Landgraf"', '')))
conferir('Cabeçalhos limitam os recursos ao próprio site', "default-src 'self'" in headers.get('Content-Security-Policy', ''))
conferir('Arquivos do banco e fontes não ficam expostos', pedir('/dados/agenda.mv.db')[0] == 404 and pedir('/src/barbearia/Banco.java')[0] == 404)
conferir('Envio sem proteção do formulário é recusado', pedir('/agendar', {'nome': 'Teste'})[0] == 403)
conferir('Formulário excessivo é recusado', pedir('/agendar', b'a' * 8193)[0] == 413)
conferir('Método inválido é recusado', pedir('/', metodo='PUT')[0] == 405)
conferir('Data inexistente tem erro legível', pedir('/?data=2026-02-31')[0] == 400)
dia = date.today() + timedelta(days=1)
while dia.weekday() != 0:
    dia += timedelta(days=1)
dados = {'token': token, 'servico': 'combo', 'barbeiro': 'lucas', 'data': str(dia), 'horario': '09:00', 'nome': 'Pessoa de Teste', 'telefone': '19988881234'}
invalidos = dict(dados, nome='<img src=x onerror=alert(1)>', telefone='123')
status, pagina, _, _ = pedir('/agendar', invalidos)
conferir('Erro preserva campos com HTML escapado', status == 400 and 'value="&lt;img src=x onerror=alert(1)&gt;"' in pagina and '<img src=x' not in pagina)
status, pagina, endereco, _ = pedir('/agendar', dados)
codigo = re.search(r'id="codigo-reserva">([A-F0-9]{16})', pagina)[1]
conferir('Reserva redireciona para a confirmação', status == 200 and '/reserva?codigo=' in endereco and 'Seu horário está reservado.' in pagina)
conferir('Confirmação não expõe nome nem telefone', dados['nome'] not in pagina and dados['telefone'] not in pagina)
status, pagina, _, _ = pedir('/agendar', dados)
conferir('Reenvio não duplica a reserva', status == 400 and 'não está disponível' in pagina)
conferir('Telefone incorreto não permite consultar', pedir('/consultar', {'token': token, 'codigo': codigo, 'telefone': '19988880000'})[0] == 400)
status, pagina, _, _ = pedir('/consultar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone']})
conferir('Consulta válida libera opção de cancelamento', status == 200 and 'form-cancelar' in pagina)
conferir('Cancelamento exige confirmação', pedir('/cancelar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone']})[0] == 400)
status, pagina, _, _ = pedir('/cancelar', {'token': token, 'codigo': codigo, 'telefone': dados['telefone'], 'confirmacao': 'sim'})
conferir('Cancelamento fica registrado na confirmação', status == 200 and 'Agendamento cancelado.' in pagina)
status, pagina, _, _ = pedir('/?servico=combo&barbeiro=lucas&data=' + str(dia))
conferir('O intervalo cancelado fica livre novamente', status == 200 and "value='09:00'" in pagina)
print(str(len(resultados)) + ' verificações HTTP aprovadas.')
