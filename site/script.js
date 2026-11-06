const formularioAgendar = document.querySelector('#form-agendar');

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
