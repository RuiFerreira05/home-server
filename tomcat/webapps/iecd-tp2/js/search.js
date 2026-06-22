/**
 * Autocomplete Search & Challenge Script - search.js
 * Enables real-time case-insensitive player lookups and invitation dispatches.
 */
(function() {
    'use strict';

    let searchInput = null;
    let clearBtn = null;
    let dropdown = null;
    let loader = null;
    let resultsContainer = null;
    let debounceTimer = null;
    
    // Store gameId for active outgoing invite
    let activeOutgoingGameId = null;

    function init() {
        searchInput = document.getElementById('player-search-input');
        clearBtn = document.getElementById('clear-search-btn');
        dropdown = document.getElementById('search-autocomplete-dropdown');
        loader = document.getElementById('dropdown-loader');
        resultsContainer = document.getElementById('autocomplete-results-container');

        if (!searchInput) return; // Only execute on the dashboard page

        // Setup event listeners
        searchInput.addEventListener('input', handleSearchInput);
        clearBtn.addEventListener('click', clearSearch);
        
        // Hide autocomplete when clicking outside
        document.addEventListener('click', function(e) {
            if (dropdown && !e.target.closest('.search-box')) {
                dropdown.style.display = 'none';
            }
        });

        // Wire Cancel Outgoing Challenge button
        const cancelBtn = document.getElementById('cancel-sent-invite-btn');
        if (cancelBtn) {
            cancelBtn.onclick = cancelOutgoingInvite;
        }

        console.log("[Search] Autocomplete script loaded.");
    }

    function handleSearchInput() {
        const query = searchInput.value.trim();
        
        if (query.length === 0) {
            clearSearch();
            return;
        }

        clearBtn.style.display = 'block';
        dropdown.style.display = 'block';
        loader.style.display = 'block';
        resultsContainer.innerHTML = '';

        // Debounce input to reduce server spam
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            performSearch(query);
        }, 300);
    }

    async function performSearch(query) {
        try {
            const response = await fetch(window.contextPath + '/api/search?q=' + encodeURIComponent(query));
            if (!response.ok) {
                throw new Error("HTTP error: " + response.status);
            }
            
            const results = await response.json();
            renderResults(results);
        } catch (e) {
            console.error("Search failed:", e);
            loader.style.display = 'none';
            resultsContainer.innerHTML = '<div class="dropdown-loader text-danger">Search error occurred.</div>';
        }
    }

    function renderResults(users) {
        loader.style.display = 'none';
        resultsContainer.innerHTML = '';

        if (!users || users.length === 0) {
            resultsContainer.innerHTML = '<div class="dropdown-loader">No active players found.</div>';
            return;
        }

        users.forEach(user => {
            const item = document.createElement('div');
            item.className = 'dropdown-item';
            
            let avatarMarkup = `<div class="item-avatar-placeholder"><i class="fa-solid fa-user"></i></div>`;
            if (user.photo && user.photo !== '') {
                avatarMarkup = `<img src="${window.contextPath}/photo/${user.photo}" alt="Avatar" class="item-avatar">`;
            }

            let actionButtons = '';
            if (window.isUserAuthenticated) {
                const challengeBtnMarkup = user.online ? `
                    <button type="button" class="btn btn-primary btn-sm challenge-btn">
                        <i class="fa-solid fa-crosshairs"></i> Challenge
                    </button>
                ` : '';

                actionButtons = `
                    <div class="search-actions">
                        <a href="${window.contextPath}/profile?username=${encodeURIComponent(user.username)}" class="btn btn-outline btn-sm" style="text-decoration: none;">
                            <i class="fa-solid fa-user"></i> Profile
                        </a>
                        ${challengeBtnMarkup}
                    </div>
                `;
            } else {
                actionButtons = `
                    <a href="${window.contextPath}/profile?username=${encodeURIComponent(user.username)}" class="btn btn-primary btn-sm" style="text-decoration: none;">
                        <i class="fa-solid fa-user"></i> View Profile
                    </a>
                `;
            }

            const statusClass = user.online ? 'online' : 'offline';
            const statusTitle = user.online ? 'Online' : 'Offline';
            const statusBadge = `<span class="status-indicator-dot ${statusClass}" title="${statusTitle}"></span>`;

            let nationalityMarkup = `<i class="fa-solid fa-earth-americas"></i> Global`;
            if (user.nationality && user.nationality !== '') {
                nationalityMarkup = `<img src="https://flagcdn.com/16x12/${user.nationality.toLowerCase()}.png" alt="flag" style="border-radius: 1px; vertical-align: middle; margin-right: 4px; box-shadow: 0 1px 2px rgba(0,0,0,0.2);"> ${user.nationality}`;
            }

            item.innerHTML = `
                <div class="item-user-info">
                    ${avatarMarkup}
                    <div class="item-meta">
                        <div class="item-username-wrapper">
                            <span class="item-username">${user.username}</span>
                            ${statusBadge}
                        </div>
                        <span class="item-nationality">${nationalityMarkup}</span>
                    </div>
                </div>
                ${actionButtons}
            `;

            // Wire Challenge click event if authenticated
            if (window.isUserAuthenticated) {
                const chalBtn = item.querySelector('.challenge-btn');
                if (chalBtn) {
                    chalBtn.onclick = () => {
                        sendMatchChallenge(user.userId, user.username);
                    };
                }
            }

            resultsContainer.appendChild(item);
        });
    }

    async function sendMatchChallenge(userId, username) {
        // Close dropdown
        dropdown.style.display = 'none';
        searchInput.value = '';
        clearBtn.style.display = 'none';

        try {
            const formData = new URLSearchParams();
            formData.append('targetUserId', userId);

            const response = await fetch(window.contextPath + '/game/invite', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            });

            const data = await response.json();
            
            if (data.status === 'OK') {
                activeOutgoingGameId = data.gameId;
                
                // Show Pending sent card
                const sentCard = document.getElementById('sent-invite-card-element');
                const oppName = document.getElementById('sent-invite-opponent-name');
                
                if (sentCard && oppName) {
                    oppName.textContent = username;
                    sentCard.style.display = 'block';
                }

                if (window.PushEngine) {
                    window.PushEngine.showSystemToast("Challenge Sent", "Invitation sent to " + username + "! Waiting for confirmation.");
                }
            } else {
                alert("Failed to challenge player: " + data.message);
            }
        } catch (e) {
            console.error("Failed to challenge user:", e);
            alert("Connection error sending challenge.");
        }
    }

    async function cancelOutgoingInvite() {
        if (!activeOutgoingGameId) return;

        try {
            const formData = new URLSearchParams();
            formData.append('gameId', activeOutgoingGameId);

            const response = await fetch(window.contextPath + '/game/invite/cancel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            });

            const data = await response.json();
            if (data.status === 'OK') {
                const sentCard = document.getElementById('sent-invite-card-element');
                if (sentCard) sentCard.style.display = 'none';
                activeOutgoingGameId = null;

                if (window.PushEngine) {
                    window.PushEngine.showSystemToast("Challenge Cancelled", "Match challenge has been cancelled.");
                }
            } else {
                alert("Failed to cancel invitation: " + data.message);
            }
        } catch (e) {
            console.error("Failed to cancel invitation:", e);
            alert("Error connecting to server to cancel.");
        }
    }

    function clearSearch() {
        if (searchInput) searchInput.value = '';
        if (clearBtn) clearBtn.style.display = 'none';
        if (dropdown) dropdown.style.display = 'none';
    }

    // Run initialization
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
