/* Shared player: Play reads prepared audio; only preparation/retry sends POST. */
(() => {
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const play = document.getElementById('audioPlay');
    const speed = document.getElementById('audioSpeed');
    const retry = document.getElementById('audioRetry');
    const message = document.getElementById('audioStatus');
    let generation = 0;
    let current = null;
    let media = null;
    let timer = null;
    let controller = null;

    function stop() {
        generation++;
        clearTimeout(timer);
        controller?.abort();
        controller = null;
        if (media) {
            media.pause();
            media.removeAttribute('src');
            media.load();
            media = null;
        }
        play.textContent = 'Play';
        play.disabled = true;
        retry.hidden = true;
    }

    function unavailable(token, expired = false) {
        if (token !== generation) return;
        play.disabled = true;
        retry.hidden = expired || !current;
        message.textContent = expired
            ? 'Audio is unavailable because this dialogue has expired from history.'
            : 'Audio could not be prepared. Select Retry audio to try again.';
    }

    function ready(token, url) {
        if (token !== generation) return;
        const expected = `/api/dialogs/history/${current}/audio`;
        if (url !== expected) throw new Error('Invalid audio URL');
        const player = new Audio();
        player.preload = 'none';
        player.playbackRate = Number(speed.value);
        player.src = url;
        media = player;
        player.addEventListener('play', () => {
            if (token === generation && media === player) play.textContent = 'Pause';
        });
        player.addEventListener('pause', () => {
            if (token === generation && media === player) play.textContent = 'Play';
        });
        player.addEventListener('ended', () => {
            if (token === generation && media === player) {
                play.textContent = 'Play';
                message.textContent = 'Audio finished. Select Play to replay.';
            }
        });
        player.addEventListener('error', () => {
            if (token === generation && media === player) {
                play.textContent = 'Play';
                message.textContent = 'Audio could not be played. Select Play to retry playback.';
            }
        });
        play.disabled = false;
        retry.hidden = true;
        message.textContent = 'Audio ready.';
        // Intentionally no player.play(): readiness never starts playback.
    }

    async function request(token, prepare) {
        if (token !== generation || !current) return;
        const activeController = new AbortController();
        controller = activeController;
        const signal = activeController.signal;
        const timeout = setTimeout(() => activeController.abort(), 15000);
        const base = `/api/dialogs/history/${current}/audio`;
        try {
            const response = await fetch(prepare ? base : base + '/status', {
                method: prepare ? 'POST' : 'GET', cache: 'no-store', signal
            });
            if (token !== generation) return;
            if (response.status === 404) { unavailable(token, true); return; }
            if (!response.ok) throw new Error('Audio request failed');
            const data = await response.json();
            if (token !== generation) return;
            if (data.status === 'ready') ready(token, data.audioUrl);
            else if (data.status === 'generating') {
                play.disabled = true;
                retry.hidden = true;
                message.textContent = 'Generating audio…';
                timer = setTimeout(() => request(token, false), 1000);
            } else if (data.status === 'not_generated' || data.status === 'failed') {
                unavailable(token);
                if (data.status === 'not_generated') message.textContent = 'Audio is not available yet. Select Retry audio to prepare it.';
            } else throw new Error('Invalid audio status');
        } catch (failure) {
            if (token === generation) unavailable(token);
        } finally {
            clearTimeout(timeout);
        }
    }

    speed.addEventListener('change', () => {
        if (media) {
            media.playbackRate = Number(speed.value);
        }
    });
    play.addEventListener('click', async () => {
        if (play.disabled || !media) return;
        const token = generation;
        const player = media;
        if (!player.paused) { player.pause(); return; }
        if (player.ended) player.currentTime = 0;
        if (player.error) player.load();
        try {
            await player.play();
            if (token !== generation || media !== player) player.pause();
        } catch (failure) {
            if (token === generation) message.textContent = 'Audio could not be played. Select Play to try again.';
        }
    });
    retry.addEventListener('click', () => {
        if (retry.hidden || !current) return;
        stop();
        message.textContent = 'Generating audio…';
        request(generation, true);
    });
    window.dialogueAudio = {
        set(id, prepare) {
            stop();
            current = typeof id === 'string' && uuid.test(id) ? id.toLowerCase() : null;
            if (!current) {
                message.textContent = 'Audio requires a dialogue saved to history.';
                return;
            }
            message.textContent = prepare ? 'Generating audio…' : 'Checking audio…';
            request(generation, prepare);
        }
    };
    window.addEventListener('pagehide', stop);
})();
