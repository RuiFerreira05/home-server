/**
 * Dynamic Push Polling Engine - push.js
 * Periodically polls the server for raw TCP pushes bridged to HTTP JSON queues.
 */
(function() {
    'use strict';

    const POLL_INTERVAL_MS = 2000;
    let pollTimer = null;
    
    // Track active invitation items: gameId -> { toast: element, card: element, fromUsername: string }
    const activeInvites = new Map();

    function init() {
        startPolling();
        logger("Push engine initialized.");
    }

    function startPolling() {
        if (pollTimer) return;
        pollTimer = setInterval(fetchPushes, POLL_INTERVAL_MS);
    }

    function stopPolling() {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    async function fetchPushes() {
        try {
            const response = await fetch(window.contextPath + '/api/push');
            if (response.status === 401) {
                // Not authenticated, stop polling
                stopPolling();
                return;
            }
            if (!response.ok) {
                throw new Error("HTTP error: " + response.status);
            }
            
            const pushes = await response.json();
            if (pushes && pushes.length > 0) {
                pushes.forEach(handlePushEvent);
            }
        } catch (e) {
            console.error("Failed to poll pushes:", e);
        }
    }

    function handlePushEvent(event) {
        logger("Handling push event: " + event.action);
        
        switch (event.action) {
            case "GAME_INVITE":
                handleIncomingInvite(event);
                break;
            case "GAME_INVITE_CANCEL":
                handleIncomingInviteCancel(event);
                break;
            case "GAME_INVITE_RESPONSE":
                handleInviteResponse(event);
                break;
            case "GAME_MOVE":
                handleIncomingMove(event);
                break;
            case "GAME_OVER":
                handleGameOver(event);
                break;
            case "GAME_OVER_DRAW":
                handleGameOverDraw(event);
                break;
            default:
                logger("Unknown push action: " + event.action);
        }
    }

    /* Incoming Game Invitation Handler */
    function handleIncomingInvite(event) {
        const gameId = event.gameId;
        const fromUsername = event.fromUsername;

        // 1. Create a beautiful custom toast element
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.innerHTML = `
            <div class="toast-header">
                <span class="toast-title"><i class="fa-solid fa-gamepad"></i> Game Challenge</span>
                <button class="toast-close" onclick="this.closest('.toast').remove()">&times;</button>
            </div>
            <div class="toast-body">
                <strong>${fromUsername}</strong> has invited you to a match of Dots &amp; Boxes!
            </div>
            <div class="toast-actions">
                <button class="btn btn-primary btn-sm accept-btn">Accept</button>
                <button class="btn btn-outline-danger btn-sm decline-btn">Decline</button>
            </div>
        `;

        // Wire accept click event
        toast.querySelector('.accept-btn').onclick = async () => {
            await respondToInvite(gameId, true);
        };

        // Wire decline click event
        toast.querySelector('.decline-btn').onclick = async () => {
            await respondToInvite(gameId, false);
        };

        // Remove duplicate invite if exists
        removeInviteFromUI(gameId);

        // Inject toast into container
        const toastContainer = document.getElementById('toast-container');
        if (toastContainer) {
            toastContainer.appendChild(toast);
        }

        // 2. Create card element for Dashboard "Pending Game Invites" card
        let cardItem = null;
        const listContainer = document.getElementById('active-invites-list');
        const fallbackContainer = document.getElementById('no-invites-fallback');

        if (listContainer) {
            cardItem = document.createElement('div');
            cardItem.className = 'invite-item';
            cardItem.setAttribute('data-game-id', gameId);
            cardItem.innerHTML = `
                <div class="invite-item-info">
                    <div class="invite-avatar-placeholder">
                        <i class="fa-solid fa-user"></i>
                    </div>
                    <div class="invite-text">
                        <span class="invite-sender">${fromUsername}</span>
                        <span class="invite-label">challenged you to play!</span>
                    </div>
                </div>
                <div class="invite-actions">
                    <button class="btn btn-primary btn-sm card-accept-btn"><i class="fa-solid fa-check"></i> Accept</button>
                    <button class="btn btn-outline-danger btn-sm card-decline-btn"><i class="fa-solid fa-xmark"></i> Decline</button>
                </div>
            `;

            // Wire actions in card
            cardItem.querySelector('.card-accept-btn').onclick = async () => {
                await respondToInvite(gameId, true);
            };
            cardItem.querySelector('.card-decline-btn').onclick = async () => {
                await respondToInvite(gameId, false);
            };

            listContainer.appendChild(cardItem);
            
            // Toggle visibility
            if (fallbackContainer) fallbackContainer.style.display = 'none';
            listContainer.style.display = 'flex';
        }

        // Store references in map
        activeInvites.set(gameId, {
            toast: toast,
            card: cardItem,
            fromUsername: fromUsername
        });
    }

    async function respondToInvite(gameId, accept) {
        try {
            const formData = new URLSearchParams();
            formData.append('gameId', gameId);
            formData.append('accept', accept);

            const response = await fetch(window.contextPath + '/game/invite/respond', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            });

            if (!response.ok) {
                const data = await response.json();
                alert("Invite response failed: " + (data.message || response.statusText));
                return;
            }

            // Get opponent name before deleting it from tracking map
            const invite = activeInvites.get(gameId);
            const opponentName = invite ? invite.fromUsername : "Opponent";

            // Clean up elements from UI
            removeInviteFromUI(gameId);

            if (accept) {
                // Accept: Show a launch modal, then redirect to board page
                const modal = document.getElementById('game-lobby-modal');
                const oppName = document.getElementById('lobby-opponent-name');
                if (modal) {
                    if (oppName) oppName.textContent = opponentName;
                    modal.style.display = 'flex';
                }
                
                setTimeout(() => {
                    window.location.href = window.contextPath + '/game/play?gameId=' + gameId + '&role=invitee&opponent=' + encodeURIComponent(opponentName);
                }, 1500);
            }
        } catch (e) {
            console.error("Error responding to invite:", e);
            alert("Connection error responding to invite.");
        }
    }

    function handleIncomingInviteCancel(event) {
        const gameId = event.gameId;
        const invite = activeInvites.get(gameId);
        const sender = invite ? invite.fromUsername : "A player";
        
        removeInviteFromUI(gameId);
        showSystemToast("Invitation Withdrawn", "Game challenge from " + sender + " has been cancelled.");
    }

    /* Outgoing Invite Response Handler */
    function handleInviteResponse(event) {
        const gameId = event.gameId;
        const accepted = event.accepted;
        const opponentUsername = event.opponentUsername;

        // Hide the outgoing pending card if visible on dashboard
        const sentCard = document.getElementById('sent-invite-card-element');
        if (sentCard) sentCard.style.display = 'none';

        if (accepted) {
            showSystemToast("Lobby Found", "Your challenge to " + opponentUsername + " was accepted! Entering room...");
            
            const modal = document.getElementById('game-lobby-modal');
            const oppName = document.getElementById('lobby-opponent-name');
            if (modal) {
                if (oppName) oppName.textContent = opponentUsername;
                modal.style.display = 'flex';
            }

            setTimeout(() => {
                window.location.href = window.contextPath + '/game/play?gameId=' + gameId + '&role=inviter&opponent=' + encodeURIComponent(opponentUsername);
            }, 1500);
        } else {
            showSystemToast("Challenge Refused", opponentUsername + " declined your match invitation.", true);
        }
    }

    /* In-game Move Handler */
    function handleIncomingMove(event) {
        // Verify if this move push is for the currently viewed board
        const activeGameId = window.gameBootstrap ? window.gameBootstrap.gameId : null;
        if (activeGameId && activeGameId === event.gameId) {
            if (window.DotsAndBoxesEngine && typeof window.DotsAndBoxesEngine.applyOpponentMove === 'function') {
                window.DotsAndBoxesEngine.applyOpponentMove(event.move);
            }
        } else {
            // Push is for another game, or we are on the dashboard/profile
            if (event.yourTurn) {
                const opponent = event.opponentName || "Your opponent";
                showSystemToast("Your Turn!", opponent + " drew a line in another active match. It's now your turn to play!");
            }
        }
    }

    /* In-game GameOver Handlers */
    function handleGameOver(event) {
        // Verify if this game over push is for the currently viewed board
        const activeGameId = window.gameBootstrap ? window.gameBootstrap.gameId : null;
        if (activeGameId && activeGameId === event.gameId) {
            if (window.DotsAndBoxesEngine && typeof window.DotsAndBoxesEngine.triggerGameOver === 'function') {
                window.DotsAndBoxesEngine.triggerGameOver(event);
                return;
            }
        }
        
        // Dashboard or another match: show Toast and fade out matching dashboard card if visible
        const alertCard = document.querySelector(`.active-match-alert a[href*="gameId=${event.gameId}"]`);
        if (alertCard) {
            const banner = alertCard.closest('.active-match-alert');
            if (banner) {
                banner.style.opacity = '0';
                setTimeout(() => banner.remove(), 400);
            }
        }
        showSystemToast("Match Finished", "Match against " + event.winnerUsername + " has finished!");
    }

    function handleGameOverDraw(event) {
        // Verify if this draw push is for the currently viewed board
        const activeGameId = window.gameBootstrap ? window.gameBootstrap.gameId : null;
        if (activeGameId && activeGameId === event.gameId) {
            if (window.DotsAndBoxesEngine && typeof window.DotsAndBoxesEngine.triggerGameOver === 'function') {
                window.DotsAndBoxesEngine.triggerGameOver({ draw: true, gameId: event.gameId });
                return;
            }
        }
        
        // Dashboard or another match: show Toast and fade out matching dashboard card if visible
        const alertCard = document.querySelector(`.active-match-alert a[href*="gameId=${event.gameId}"]`);
        if (alertCard) {
            const banner = alertCard.closest('.active-match-alert');
            if (banner) {
                banner.style.opacity = '0';
                setTimeout(() => banner.remove(), 400);
            }
        }
        showSystemToast("Match Finished", "Match ended in a DRAW!");
    }

    /* Utility UI cleanup of active items */
    function removeInviteFromUI(gameId) {
        const invite = activeInvites.get(gameId);
        if (!invite) return;

        // 1. Fade out and remove Toast notification
        const toast = invite.toast;
        if (toast) {
            toast.classList.add('removing');
            setTimeout(() => toast.remove(), 300);
        }

        // 2. Remove card list item
        const card = invite.card;
        if (card) {
            card.remove();
        }

        // 3. Remove entry from tracking map
        activeInvites.delete(gameId);

        // 4. Update Dashboard invites list card placeholders
        const listContainer = document.getElementById('active-invites-list');
        const fallbackContainer = document.getElementById('no-invites-fallback');
        
        if (listContainer && fallbackContainer) {
            if (activeInvites.size === 0) {
                listContainer.style.display = 'none';
                fallbackContainer.style.display = 'block';
            }
        }
    }

    function showSystemToast(title, bodyText, isAlert = false) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        if (isAlert) {
            toast.style.borderLeftColor = 'var(--text-danger)';
        } else {
            toast.style.borderLeftColor = 'var(--accent)';
        }
        
        toast.innerHTML = `
            <div class="toast-header">
                <span class="toast-title">
                    <i class="${isAlert ? 'fa-solid fa-circle-exclamation' : 'fa-solid fa-info-circle'}"></i> 
                    ${title}
                </span>
                <button class="toast-close" onclick="this.closest('.toast').remove()">&times;</button>
            </div>
            <div class="toast-body">
                ${bodyText}
            </div>
        `;

        const container = document.getElementById('toast-container');
        if (container) {
            container.appendChild(toast);
            setTimeout(() => {
                toast.classList.add('removing');
                setTimeout(() => toast.remove(), 300);
            }, 6000); // Remove system toasts automatically after 6 seconds
        }
    }

    function logger(msg) {
        console.log("[PushEngine] " + msg);
    }

    // Expose utility globally
    window.PushEngine = {
        showSystemToast: showSystemToast,
        stopPolling: stopPolling,
        startPolling: startPolling
    };

    // Run initialization
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
