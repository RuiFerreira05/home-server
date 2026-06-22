/**
 * HTML5 Canvas Dots & Boxes Game Engine - game.js
 * Renders the 5x5 interactive board, tracks mouse vectors, submits moves via AJAX,
 * and processes real-time TCP pushes from opponent.
 */
(function() {
    'use strict';

    // Game Constants
    const DOTS_COUNT = 5;
    const LINE_THICKNESS = 6;
    const HOVER_TOLERANCE = 24; // Mouse hover radius around line midpoints

    let canvas = null;
    let ctx = null;
    let config = null;

    // Board Geometry
    let padding = 50;
    let cellSpacing = 95; // Spacing between adjacent dots

    // Local Game State
    let player1 = { id: null, name: "Player 1", score: 0 };
    let player2 = { id: null, name: "Player 2", score: 0 };
    
    let isMyTurn = false;
    let myColor = "#ef4444"; // Red for P1, Blue for P2
    let oppColor = "#3b82f6";
    
    const drawnLines = new Set(); // Stores "x1,y1,x2,y2" normalized strings
    const capturedBoxes = new Map(); // "x,y" -> playerId
    
    let hoverLine = null; // Current hover line: { x1, y1, x2, y2, type: 'H'|'V' }
    let countdownInterval = null;
    let secondsLeft = 30;

    function init() {
        canvas = document.getElementById('game-board-canvas');
        if (!canvas) return; // Not on game page

        ctx = canvas.getContext('2d');
        config = window.gameBootstrap;

        if (!config || !config.gameId || config.gameId === 'null') {
            console.error("Missing game bootstrap config.");
            alert("Error initializing match session.");
            return;
        }

        // Set up player metadata based on role
        if (config.role === 'inviter') {
            // Inviter is Player 1 (Red, moves first)
            player1.id = config.userId;
            player1.name = config.username;
            isMyTurn = true;
            myColor = "#ef4444";
            oppColor = "#3b82f6";
        } else {
            // Invitee is Player 2 (Blue, moves second)
            player2.id = config.userId;
            player2.name = config.username;
            isMyTurn = false;
            myColor = "#3b82f6";
            oppColor = "#ef4444";
        }

        // Setup scores from bootstrap hydration
        if (config.initialP1Score !== undefined) {
            player1.score = parseInt(config.initialP1Score, 10) || 0;
        }
        if (config.initialP2Score !== undefined) {
            player2.score = parseInt(config.initialP2Score, 10) || 0;
        }
        if (config.initialIsMyTurn !== undefined) {
            isMyTurn = (config.initialIsMyTurn === true || config.initialIsMyTurn === 'true');
        }

        // Setup Canvas sizing based on container
        resizeCanvas();

        // Hydrate recovery game state (if present)
        if (config.initialLines && config.initialLines.length > 0) {
            config.initialLines.forEach(lineStr => {
                drawnLines.add(lineStr);
            });
        }
        if (config.initialBoxes && config.initialBoxes.length > 0) {
            config.initialBoxes.forEach(box => {
                const ownerId = box.owner === 'me' ? config.userId : 'opponent';
                capturedBoxes.set(box.key, ownerId);
            });
        }

        // Register Mouse Listeners
        canvas.addEventListener('mousemove', handleMouseMove);
        canvas.addEventListener('mouseleave', handleMouseLeave);
        canvas.addEventListener('click', handleCanvasClick);

        // Wire Surrender Button
        const surrenderBtn = document.getElementById('surrender-match-btn');
        if (surrenderBtn) {
            surrenderBtn.onclick = handleSurrender;
        }

        // Expose Engine methods globally for Push polling sync
        window.DotsAndBoxesEngine = {
            applyOpponentMove: applyOpponentMove,
            triggerGameOver: triggerGameOver
        };

        // Fetch initial scoreboard metadata
        updateScoreboardUI();
        
        // Start Render Loop
        drawBoard();
        console.log("[GameEngine] Bootstrap complete. Turn state: " + isMyTurn);
    }

    function resizeCanvas() {
        const width = canvas.parentElement.clientWidth;
        canvas.width = Math.min(width, 480);
        canvas.height = canvas.width;
        
        padding = canvas.width * 0.12;
        cellSpacing = (canvas.width - padding * 2) / (DOTS_COUNT - 1);
    }

    // Translate grid coordinate index (0-4) to Canvas screen X/Y pixels
    function gridToPixel(index) {
        return padding + index * cellSpacing;
    }

    // Convert Canvas screen pixels to grid coordinates if near a dot
    function pixelToGrid(px) {
        return Math.round((px - padding) / cellSpacing);
    }

    /* Core Render Loop */
    function drawBoard() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // 1. Draw Captured Boxes (Fills)
        for (let x = 0; x < DOTS_COUNT - 1; x++) {
            for (let y = 0; y < DOTS_COUNT - 1; y++) {
                const key = x + "," + y;
                if (capturedBoxes.has(key)) {
                    const ownerId = capturedBoxes.get(key);
                    const isMe = ownerId === config.userId;
                    
                    ctx.fillStyle = isMe ? fadeColor(myColor, 0.15) : fadeColor(oppColor, 0.15);
                    
                    const rx = gridToPixel(x) + LINE_THICKNESS/2;
                    const ry = gridToPixel(y) + LINE_THICKNESS/2;
                    const size = cellSpacing - LINE_THICKNESS;
                    
                    ctx.fillRect(rx, ry, size, size);

                    // Draw Captured Initial Text in center
                    ctx.font = "bold 22px 'Outfit'";
                    ctx.fillStyle = isMe ? myColor : oppColor;
                    ctx.textAlign = "center";
                    ctx.textBaseline = "middle";
                    
                    const myInitial = config.username.substring(0,1).toUpperCase();
                    const oppInitial = config.opponentName ? config.opponentName.substring(0,1).toUpperCase() : "O";
                    
                    let pInitial;
                    if (myInitial === oppInitial) {
                        if (config.role === 'inviter') {
                            pInitial = isMe ? "1" : "2";
                        } else {
                            pInitial = isMe ? "2" : "1";
                        }
                    } else {
                        pInitial = isMe ? myInitial : oppInitial;
                    }
                    ctx.fillText(pInitial, rx + size/2, ry + size/2);
                }
            }
        }

        // 2. Draw Hover Line Preview (if valid and it's my turn)
        if (hoverLine && isMyTurn) {
            ctx.strokeStyle = fadeColor(myColor, 0.45);
            ctx.lineWidth = LINE_THICKNESS;
            ctx.lineCap = "round";
            
            ctx.beginPath();
            ctx.moveTo(gridToPixel(hoverLine.x1), gridToPixel(hoverLine.y1));
            ctx.lineTo(gridToPixel(hoverLine.x2), gridToPixel(hoverLine.y2));
            ctx.stroke();
        }

        // 3. Draw Solid Lines
        drawnLines.forEach(lineStr => {
            const pts = lineStr.split(',').map(Number);
            const lineOwner = getLineOwner(pts[0], pts[1], pts[2], pts[3]);
            
            ctx.strokeStyle = lineOwner === config.userId ? myColor : oppColor;
            ctx.lineWidth = LINE_THICKNESS;
            ctx.lineCap = "round";
            
            ctx.beginPath();
            ctx.moveTo(gridToPixel(pts[0]), gridToPixel(pts[1]));
            ctx.lineTo(gridToPixel(pts[2]), gridToPixel(pts[3]));
            ctx.stroke();
        });

        // 4. Draw Grid Dots
        for (let i = 0; i < DOTS_COUNT; i++) {
            for (let j = 0; j < DOTS_COUNT; j++) {
                const px = gridToPixel(i);
                const py = gridToPixel(j);

                // Glow shadow
                ctx.shadowColor = "rgba(255, 255, 255, 0.1)";
                ctx.shadowBlur = 4;

                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                ctx.arc(px, py, 6, 0, Math.PI * 2);
                ctx.fill();

                // Clear shadow settings
                ctx.shadowColor = "transparent";
                ctx.shadowBlur = 0;
            }
        }
    }

    /* Mouse Hover Hitbox Handlers */
    function handleMouseMove(e) {
        if (!isMyTurn) {
            hoverLine = null;
            return;
        }

        const rect = canvas.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;

        let closestLine = null;
        let minDistance = Infinity;

        // Check horizontal line candidates
        for (let y = 0; y < DOTS_COUNT; y++) {
            for (let x = 0; x < DOTS_COUNT - 1; x++) {
                const lineKey = x + "," + y + "," + (x + 1) + "," + y;
                if (drawnLines.has(lineKey)) continue;

                // Midpoint coordinates of segment
                const midX = gridToPixel(x + 0.5);
                const midY = gridToPixel(y);
                const dist = Math.hypot(mx - midX, my - midY);

                if (dist < minDistance && dist < HOVER_TOLERANCE) {
                    minDistance = dist;
                    closestLine = { x1: x, y1: y, x2: x + 1, y2: y, key: lineKey };
                }
            }
        }

        // Check vertical line candidates
        for (let x = 0; x < DOTS_COUNT; x++) {
            for (let y = 0; y < DOTS_COUNT - 1; y++) {
                const lineKey = x + "," + y + "," + x + "," + (y + 1);
                if (drawnLines.has(lineKey)) continue;

                // Midpoint coordinates of segment
                const midX = gridToPixel(x);
                const midY = gridToPixel(y + 0.5);
                const dist = Math.hypot(mx - midX, my - midY);

                if (dist < minDistance && dist < HOVER_TOLERANCE) {
                    minDistance = dist;
                    closestLine = { x1: x, y1: y, x2: x, y2: y + 1, key: lineKey };
                }
            }
        }

        if (closestLine !== hoverLine) {
            hoverLine = closestLine;
            drawBoard();
        }
    }

    function handleMouseLeave() {
        if (hoverLine) {
            hoverLine = null;
            drawBoard();
        }
    }

    /* Click handler to submit a Move */
    async function handleCanvasClick() {
        if (!isMyTurn || !hoverLine) return;

        const moveStr = hoverLine.key; // "x1,y1,x2,y2"
        hoverLine = null;

        try {
            const formData = new URLSearchParams();
            formData.append('gameId', config.gameId);
            formData.append('move', moveStr);

            const response = await fetch(window.contextPath + '/game/move', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            });

            const data = await response.json();
            if (data.status === 'OK') {
                // Apply move locally
                recordLineLocally(moveStr, config.userId);
                
                // Check completed boxes
                const pointsEarned = checkAndCaptureBoxes(moveStr, config.userId);
                
                if (pointsEarned > 0) {
                    if (config.role === 'inviter') {
                        player1.score += pointsEarned;
                    } else {
                        player2.score += pointsEarned;
                    }
                    // Player retains turn on capture!
                    isMyTurn = true;
                } else {
                    // Pass turn to opponent
                    isMyTurn = false;
                }

                updateScoreboardUI();
                drawBoard();
            } else {
                alert("Move rejected: " + data.message);
            }
        } catch (e) {
            console.error("Failed to submit move:", e);
            alert("Network error making move.");
        }
    }

    /* Sync opponent's move push */
    function applyOpponentMove(moveStr) {
        // Opponent's ID
        const opponentId = "opponent";
        
        recordLineLocally(moveStr, opponentId);
        const pointsEarned = checkAndCaptureBoxes(moveStr, opponentId);

        if (pointsEarned > 0) {
            if (config.role === 'inviter') {
                player2.score += pointsEarned;
            } else {
                player1.score += pointsEarned;
            }
            // Opponent captured, they retain turn
            isMyTurn = false;
        } else {
            // Give turn back to me
            isMyTurn = true;
        }

        updateScoreboardUI();
        drawBoard();

        if (isMyTurn && window.PushEngine) {
            window.PushEngine.showSystemToast("Your Turn!", "It's your turn to draw a line.");
        }
    }

    /* Box completion checks */
    function checkAndCaptureBoxes(moveStr, playerId) {
        const pts = moveStr.split(',').map(Number);
        const x1 = pts[0], y1 = pts[1];
        const x2 = pts[2], y2 = pts[3];
        
        let capturedCount = 0;

        if (y1 === y2) { // Horizontal line drawn
            if (y1 > 0 && isBoxClosed(x1, y1 - 1)) {
                if (captureBox(x1, y1 - 1, playerId)) capturedCount++;
            }
            if (y1 < DOTS_COUNT - 1 && isBoxClosed(x1, y1)) {
                if (captureBox(x1, y1, playerId)) capturedCount++;
            }
        } else { // Vertical line drawn
            if (x1 > 0 && isBoxClosed(x1 - 1, y1)) {
                if (captureBox(x1 - 1, y1, playerId)) capturedCount++;
            }
            if (x1 < DOTS_COUNT - 1 && isBoxClosed(x1, y1)) {
                if (captureBox(x1, y1, playerId)) capturedCount++;
            }
        }
        return capturedCount;
    }

    function isBoxClosed(x, y) {
        return drawnLines.has(x + "," + y + "," + (x + 1) + "," + y) &&
               drawnLines.has(x + "," + (y + 1) + "," + (x + 1) + "," + (y + 1)) &&
               drawnLines.has(x + "," + y + "," + x + "," + (y + 1)) &&
               drawnLines.has((x + 1) + "," + y + "," + (x + 1) + "," + (y + 1));
    }

    function captureBox(x, y, playerId) {
        const key = x + "," + y;
        if (!capturedBoxes.has(key)) {
            capturedBoxes.set(key, playerId);
            return true;
        }
        return false;
    }

    function recordLineLocally(moveStr, ownerId) {
        // Normalize points: x1 <= x2, y1 <= y2
        const pts = moveStr.split(',').map(Number);
        let x1 = pts[0], y1 = pts[1], x2 = pts[2], y2 = pts[3];
        
        if (x1 > x2 || (x1 === x2 && y1 > y2)) {
            let tx = x1; x1 = x2; x2 = tx;
            let ty = y1; y1 = y2; y2 = ty;
        }

        const key = x1 + "," + y1 + "," + x2 + "," + y2;
        drawnLines.add(key);
        
        // Save owner index mapping
        localStorage.setItem("line_owner_" + config.gameId + "_" + key, ownerId);
    }

    function getLineOwner(x1, y1, x2, y2) {
        return localStorage.getItem("line_owner_" + config.gameId + "_" + x1 + "," + y1 + "," + x2 + "," + y2);
    }

    /* Scoreboard UI Updates */
    function updateScoreboardUI() {
        const p1NameEl = document.getElementById('player1-name');
        const p2NameEl = document.getElementById('player2-name');
        const p1ScoreEl = document.getElementById('player1-score-val');
        const p2ScoreEl = document.getElementById('player2-score-val');
        const turnWidget = document.getElementById('turn-widget');
        const turnText = document.getElementById('turn-text-element');
        const turnIcon = document.getElementById('turn-pulse-icon-element');

        if (config.role === 'inviter') {
            p1NameEl.textContent = config.username + " (You)";
            p2NameEl.textContent = config.opponentName || "Opponent";
        } else {
            p1NameEl.textContent = config.opponentName || "Opponent";
            p2NameEl.textContent = config.username + " (You)";
        }

        p1ScoreEl.textContent = player1.score;
        p2ScoreEl.textContent = player2.score;

        // Apply turn classes
        if (turnWidget) {
            turnWidget.className = "turn-indicator-widget " + (isMyTurn ? "my-turn" : "opponent-turn");
            turnText.textContent = isMyTurn ? "Your Turn" : "Opponent's Turn";
            turnIcon.innerHTML = isMyTurn ? 
                '<i class="fa-solid fa-play fa-beat"></i>' : 
                '<i class="fa-solid fa-hourglass-start fa-spin"></i>';
        }

        // Reset and run turn move countdown
        if (countdownInterval) {
            clearInterval(countdownInterval);
            countdownInterval = null;
        }

        secondsLeft = 30;
        const timerValEl = document.getElementById('timer-countdown-val');
        if (timerValEl) {
            timerValEl.textContent = secondsLeft;
            timerValEl.style.color = isMyTurn ? "#22c55e" : "#ef4444";
        }

        countdownInterval = setInterval(() => {
            secondsLeft--;
            if (timerValEl) {
                timerValEl.textContent = Math.max(0, secondsLeft);
            }
            if (secondsLeft <= 0) {
                clearInterval(countdownInterval);
                countdownInterval = null;
                if (isMyTurn) {
                    isMyTurn = false;
                    if (turnWidget) {
                        turnWidget.className = "turn-indicator-widget opponent-turn";
                        turnText.textContent = "Time's Up! Waiting...";
                        turnIcon.innerHTML = '<i class="fa-solid fa-hourglass-end fa-fade"></i>';
                    }
                    console.log("[GameEngine] Turn time expired. Waiting for forfeit from server.");
                }
            }
        }, 1000);
    }

    /* Surrender click handler */
    async function handleSurrender() {
        if (!confirm("Are you sure you want to surrender this match? You will receive a loss.")) return;

        try {
            const formData = new URLSearchParams();
            formData.append('gameId', config.gameId);

            const response = await fetch(window.contextPath + '/game/surrender', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            });

            // Clean up and route back
            window.location.href = window.contextPath + '/dashboard?msg=You+surrendered+the+match';
        } catch (e) {
            console.error("Surrender post failed:", e);
            window.location.href = window.contextPath + '/dashboard';
        }
    }

    /* Game Over Modal trigger */
    function triggerGameOver(event) {
        isMyTurn = false;
        if (countdownInterval) {
            clearInterval(countdownInterval);
            countdownInterval = null;
        }
        
        const modal = document.getElementById('game-over-modal');
        const iconWrapper = document.getElementById('game-over-icon-wrapper');
        const icon = document.getElementById('game-over-icon');
        const title = document.getElementById('game-over-title');
        const summary = document.getElementById('game-over-summary');
        
        const mp1Name = document.getElementById('m-p1-name');
        const mp2Name = document.getElementById('m-p2-name');
        const mp1Score = document.getElementById('m-p1-score');
        const mp2Score = document.getElementById('m-p2-score');

        // Set scores
        mp1Score.textContent = player1.score;
        mp2Score.textContent = player2.score;
        
        if (config.role === 'inviter') {
            mp1Name.textContent = config.username;
            mp2Name.textContent = config.opponentName || "Opponent";
        } else {
            mp1Name.textContent = config.opponentName || "Opponent";
            mp2Name.textContent = config.username;
        }

        // Determine outcome
        let outcome = 'DRAW';
        if (event.winnerId) {
            outcome = event.winnerId === config.userId ? 'WIN' : 'LOSS';
        }

        // Configure colors and icons
        if (iconWrapper) {
            iconWrapper.className = "modal-icon";
            if (outcome === 'WIN') {
                iconWrapper.classList.add('win-style');
                icon.className = "fa-solid fa-trophy fa-bounce";
                title.textContent = "Victory!";
                title.style.color = "var(--text-success)";
                summary.textContent = event.reason === 'TIMEOUT' ? 
                    "Your opponent ran out of time!" : 
                    "Congratulations! You won the Dots & Boxes match!";
            } else if (outcome === 'LOSS') {
                iconWrapper.classList.add('loss-style');
                icon.className = "fa-solid fa-skull-crossbones fa-shake";
                title.textContent = "Defeat";
                title.style.color = "var(--text-danger)";
                summary.textContent = event.reason === 'SURRENDER' ? 
                    "You surrendered the match." : 
                    (event.reason === 'TIMEOUT' ? 
                     "You ran out of time!" : 
                     "Better luck next time! Your opponent took the victory.");
            } else {
                iconWrapper.classList.add('draw-style');
                icon.className = "fa-solid fa-scale-balanced";
                title.textContent = "Draw Match";
                title.style.color = "var(--text-warning)";
                summary.textContent = "Wow! A neck-and-neck draw! The scores were equal.";
            }
        }

        // Display Modal
        if (modal) {
            modal.style.display = 'flex';
        }
    }

    /* Helper: parse hex color and fade it */
    function fadeColor(hex, opacity) {
        // Strip # if present
        hex = hex.replace('#', '');
        const r = parseInt(hex.substring(0, 2), 16);
        const g = parseInt(hex.substring(2, 4), 16);
        const b = parseInt(hex.substring(4, 6), 16);
        return `rgba(${r}, ${g}, ${b}, ${opacity})`;
    }

    // Trigger initialization
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
