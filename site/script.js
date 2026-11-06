const formularioAgendar = document.querySelector('#form-agendar');

document.querySelectorAll('.pagina-interna .horarios, .pagina-interna .lista-reservas').forEach(function (lista) {
    const itens = Array.from(lista.children).filter(function (item) {
        return item.matches('.hora, .cartao-reserva');
    });
    if (itens.length === 0) return;

    const horarios = lista.classList.contains('horarios');
    const navegacao = document.createElement('div');
    const anterior = document.createElement('button');
    const proximo = document.createElement('button');
    const contador = document.createElement('span');
    navegacao.className = 'paginacao';
    anterior.type = 'button';
    proximo.type = 'button';
    anterior.textContent = '← Anterior';
    proximo.textContent = 'Próximos →';
    anterior.setAttribute('aria-label', horarios ? 'Horários anteriores' : 'Agendamentos anteriores');
    proximo.setAttribute('aria-label', horarios ? 'Próximos horários' : 'Próximos agendamentos');
    contador.setAttribute('aria-live', 'polite');
    navegacao.append(anterior, contador, proximo);
    lista.after(navegacao);

    let limite = quantidadePorPagina();
    const selecionado = itens.findIndex(function (item) { return item.querySelector('input:checked'); });
    let pagina = Math.floor(Math.max(0, selecionado) / limite);

    function quantidadePorPagina() {
        const celular = window.innerWidth <= 760;
        return horarios ? (celular ? 8 : itens.length) : (celular || window.innerHeight < 700 ? 1 : 2);
    }

    function mostrarPagina() {
        const inicio = pagina * limite;
        itens.forEach(function (item, indice) {
            const visivel = indice >= inicio && indice < inicio + limite;
            item.hidden = !visivel;
            const entrada = item.querySelector('input');
            if (entrada) {
                entrada.disabled = !visivel;
                if (!visivel && entrada.checked) {
                    entrada.checked = false;
                    document.querySelector('#horario-escolhido').textContent = 'Escolha um horário acima';
                }
            }
        });
        navegacao.hidden = itens.length <= limite;
        anterior.disabled = pagina === 0;
        proximo.disabled = inicio + limite >= itens.length;
        contador.textContent = (inicio + 1) + '–' + Math.min(inicio + limite, itens.length) + ' de ' + itens.length;
    }

    anterior.addEventListener('click', function () { pagina--; mostrarPagina(); });
    proximo.addEventListener('click', function () { pagina++; mostrarPagina(); });
    window.addEventListener('resize', function () {
        const novoLimite = quantidadePorPagina();
        if (novoLimite !== limite) {
            limite = novoLimite;
            pagina = 0;
            mostrarPagina();
        }
    });
    mostrarPagina();
});

if (formularioAgendar) {
    formularioAgendar.querySelectorAll('input[name="horario"]').forEach(function (horario) {
        horario.addEventListener('change', function () {
            document.querySelector('#horario-escolhido').textContent = 'Horário escolhido: ' + horario.value;
        });
    });
}

const copiarCodigo = document.querySelector('#copiar-codigo');

if (copiarCodigo) {
    copiarCodigo.addEventListener('click', async function () {
        const codigo = document.querySelector('#codigo-reserva').textContent;
        const retorno = document.querySelector('#retorno-copia');
        try {
            await navigator.clipboard.writeText(codigo);
            retorno.textContent = 'Código copiado. Guarde em um lugar fácil de encontrar.';
        } catch (erro) {
            retorno.textContent = 'Selecione o código acima e copie para guardar.';
        }
    });
}

document.querySelectorAll('form[method="post"]').forEach(function (formulario) {
    formulario.addEventListener('submit', function () {
        const botao = formulario.querySelector('button[type="submit"]');
        if (botao) {
            botao.disabled = true;
            botao.textContent = 'Só um instante…';
        }
    });
});

window.addEventListener('pageshow', function (evento) {
    if (evento.persisted) window.location.reload();
});
