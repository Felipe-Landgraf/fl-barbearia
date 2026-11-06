const botaoMenu = document.querySelector('.menu-botao');
const navegacao = document.querySelector('#navegacao');

botaoMenu.addEventListener('click', function () {
    const aberto = botaoMenu.getAttribute('aria-expanded') === 'true';
    botaoMenu.setAttribute('aria-expanded', String(!aberto));
    navegacao.classList.toggle('aberta', !aberto);
});

navegacao.querySelectorAll('a').forEach(function (link) {
    link.addEventListener('click', function () {
        botaoMenu.setAttribute('aria-expanded', 'false');
        navegacao.classList.remove('aberta');
    });
});

document.addEventListener('keydown', function (evento) {
    if (evento.key === 'Escape' && botaoMenu.getAttribute('aria-expanded') === 'true') {
        botaoMenu.setAttribute('aria-expanded', 'false');
        navegacao.classList.remove('aberta');
        botaoMenu.focus();
    }
});

const consultaHorarios = document.querySelector('#consulta-horarios');
const botaoConfirmar = document.querySelector('#confirmar');

if (consultaHorarios) {
    consultaHorarios.addEventListener('change', function () {
        document.querySelector('#aviso-atualizar').hidden = false;
        botaoConfirmar.disabled = true;
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
