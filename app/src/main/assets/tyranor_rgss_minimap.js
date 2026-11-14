(function () {
  "use strict";

  var FALLBACK_COLORS = {
    blocked: "#4f5952",
    passable: "#cbd5ce",
    grid: "rgba(83,96,90,.22)",
    blockedGrid: "rgba(220,230,224,.14)",
    boundary: "#748079",
    eventFill: "#3578ba",
    eventText: "#ffffff",
    eventStroke: "#b9c4bc",
    player: "#18b978",
    playerStroke: "#ffffff",
    teleport: "#8655b6",
    selection: "#137451",
    selectionFill: "rgba(24,185,120,.18)",
    movable: "#dd862b",
    itemChange: "#c85b67"
  };

  function getColors(element) {
    var style = window.getComputedStyle ? window.getComputedStyle(element) : null;
    function color(name, fallbackName) {
      return style && style.getPropertyValue("--tm-map-" + name).trim() || FALLBACK_COLORS[fallbackName || name];
    }
    function withAlpha(value, alpha) {
      var match = String(value || "").match(/^#([0-9a-f]{6})$/i);
      if (!match) return "";
      var hex = match[1];
      return "rgba(" + parseInt(hex.slice(0, 2), 16) + "," + parseInt(hex.slice(2, 4), 16) + "," + parseInt(hex.slice(4, 6), 16) + "," + alpha + ")";
    }
    var accent = style && style.getPropertyValue("--tm-primary").trim();
    return {
      blocked: color("blocked"), passable: color("passable"), grid: color("grid"),
      blockedGrid: color("blocked-grid", "blockedGrid"), boundary: color("boundary"),
      eventFill: color("event-fill", "eventFill"), eventText: color("event-text", "eventText"), eventStroke: color("event-stroke", "eventStroke"),
      player: color("player"), playerStroke: color("player-stroke", "playerStroke"), teleport: color("teleport"),
      selection: accent || color("selection"),
      selectionFill: withAlpha(accent, 0.18) || color("selection-fill", "selectionFill"),
      movable: color("movable"), itemChange: color("item-change", "itemChange")
    };
  }

  function roundedRect(context, x, y, width, height, radius) {
    var r = Math.max(0, Math.min(radius, width / 2, height / 2));
    context.beginPath();
    context.moveTo(x + r, y);
    context.arcTo(x + width, y, x + width, y + height, r);
    context.arcTo(x + width, y + height, x, y + height, r);
    context.arcTo(x, y + height, x, y, r);
    context.arcTo(x, y, x + width, y, r);
    context.closePath();
  }

  function mount(options) {
    var canvas = options && options.canvas;
    var viewport = options && options.viewport;
    var data = options && options.data;
    var slider = options && options.slider;
    var zoomValue = options && options.zoomValue;
    var onCell = options && options.onCell;
    var onClear = options && options.onClear;
    if (!canvas || !viewport || !data || !(Number(data.width) > 0) || !(Number(data.height) > 0)) return null;

    var width = Number(data.width);
    var height = Number(data.height);
    var rows = data.rows || [];
    var events = data.events || [];
    var cellSize = 1;
    var resizeHandler = null;
    var resizeObserver = null;
    var groups = Object.create(null);
    var pointerState = null;
    var selectedCell = null;
    var suppressNextClick = false;
    var suppressTimer = null;

    function indexEvents() {
      groups = Object.create(null);
      events.forEach(function (event) {
        var x = Number(event.x), y = Number(event.y);
        if (!Number.isInteger(x) || !Number.isInteger(y) || x < 0 || y < 0 || x >= width || y >= height) return;
        var key = x + "," + y;
        if (!groups[key]) groups[key] = [];
        groups[key].push(event);
      });
    }
    indexEvents();

    function render() {
      var availableWidth = viewport.clientWidth;
      var availableHeight = viewport.clientHeight;
      if (availableWidth <= 0 || availableHeight <= 0) return;

      var fitSize = Math.min(availableWidth / width, availableHeight / height);
      var zoom = Math.max(100, Number(slider && slider.value) || 100) / 100;
      cellSize = fitSize * zoom;
      var cssWidth = width * cellSize;
      var cssHeight = height * cellSize;
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      var colors = getColors(canvas);
      canvas.style.width = cssWidth + "px";
      canvas.style.height = cssHeight + "px";
      canvas.style.display = "block";
      canvas.width = Math.max(1, Math.ceil(cssWidth * dpr));
      canvas.height = Math.max(1, Math.ceil(cssHeight * dpr));

      var context = canvas.getContext("2d");
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      for (var y = 0; y < height; y++) {
        var row = String(rows[y] || "");
        for (var x = 0; x < width; x++) {
          context.fillStyle = row.charAt(x) === "1" ? colors.passable : colors.blocked;
          context.fillRect(x * cellSize, y * cellSize, cellSize + 0.25, cellSize + 0.25);
        }
      }

      var passableGridSegments = [];
      var blockedGridSegments = [];
      function addGridSegment(segments, x1, y1, x2, y2) {
        segments.push(x1, y1, x2, y2);
      }
      for (var rowIndex = 0; rowIndex < height; rowIndex++) {
        var gridRow = String(rows[rowIndex] || "");
        for (var column = 1; column < width; column++) {
          var verticalSegments = gridRow.charAt(column - 1) === "1" || gridRow.charAt(column) === "1"
            ? passableGridSegments : blockedGridSegments;
          var lineX = column * cellSize;
          addGridSegment(verticalSegments, lineX, rowIndex * cellSize, lineX, (rowIndex + 1) * cellSize);
        }
      }
      for (var rowBoundary = 1; rowBoundary < height; rowBoundary++) {
        var aboveRow = String(rows[rowBoundary - 1] || "");
        var belowRow = String(rows[rowBoundary] || "");
        var lineY = rowBoundary * cellSize;
        for (var gridColumn = 0; gridColumn < width; gridColumn++) {
          var horizontalSegments = aboveRow.charAt(gridColumn) === "1" || belowRow.charAt(gridColumn) === "1"
            ? passableGridSegments : blockedGridSegments;
          addGridSegment(horizontalSegments, gridColumn * cellSize, lineY, (gridColumn + 1) * cellSize, lineY);
        }
      }
      var gridLineWidth = Math.max(0.45, Math.min(1, cellSize * 0.05));
      function drawGridSegments(segments, color) {
        if (!segments.length) return;
        context.beginPath();
        for (var index = 0; index < segments.length; index += 4) {
          context.moveTo(segments[index], segments[index + 1]);
          context.lineTo(segments[index + 2], segments[index + 3]);
        }
        context.strokeStyle = color;
        context.lineWidth = gridLineWidth;
        context.stroke();
      }
      drawGridSegments(passableGridSegments, colors.grid);
      drawGridSegments(blockedGridSegments, colors.blockedGrid);

      if (selectedCell) {
        var selectedX = selectedCell.x * cellSize;
        var selectedY = selectedCell.y * cellSize;
        context.fillStyle = colors.selectionFill;
        context.fillRect(selectedX, selectedY, cellSize, cellSize);
        context.strokeStyle = colors.selection;
        context.lineWidth = Math.max(1, Math.min(2.5, cellSize * 0.12));
        context.strokeRect(selectedX + context.lineWidth / 2, selectedY + context.lineWidth / 2,
          Math.max(0, cellSize - context.lineWidth), Math.max(0, cellSize - context.lineWidth));
      }

      Object.keys(groups).forEach(function (key) {
        var markers = groups[key];
        var first = markers[0];
        var hasMultipleEvents = markers.length > 1;
        var centerX = (Number(first.x) + 0.5) * cellSize;
        var centerY = (Number(first.y) + 0.5) * cellSize;
        var markerSize = cellSize * 0.78;
        var markerRadius = markerSize * 0.24;
        var hasTeleport = !!(first.isTeleport || first.teleport);
        var hasMovable = !!first.isMovable;
        var hasItemChange = !!first.isItemChange;
        var markerColor = hasItemChange ? colors.itemChange : hasMovable ? colors.movable : hasTeleport ? colors.teleport : colors.eventFill;

        roundedRect(context, centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize, markerRadius);
        context.fillStyle = markerColor;
        context.fill();
        context.strokeStyle = colors.eventStroke;
        context.lineWidth = cellSize * 0.05;
        context.stroke();

        if (cellSize >= 8) {
          var label = String(first.id);
          var labelSize = cellSize * 0.62;
          context.font = "600 " + labelSize + "px system-ui,sans-serif";
          var maxLabelWidth = markerSize * 0.84;
          var measuredLabelWidth = context.measureText(label).width;
          if (measuredLabelWidth > maxLabelWidth) {
            labelSize *= maxLabelWidth / measuredLabelWidth;
            context.font = "600 " + labelSize + "px system-ui,sans-serif";
          }
          context.textAlign = "center";
          context.textBaseline = "middle";
          context.fillStyle = colors.eventText;
          context.fillText(label, centerX, centerY + 0.3);
        }
      });

      if (data.player) {
        var playerX = Number(data.player.x), playerY = Number(data.player.y);
        if (playerX >= 0 && playerY >= 0 && playerX < width && playerY < height) {
          var markerSize = Math.max(3, cellSize * 0.66);
          var centerX = (playerX + 0.5) * cellSize;
          var centerY = (playerY + 0.5) * cellSize;
          roundedRect(context, centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize, markerSize * 0.25);
          context.fillStyle = colors.player;
          context.fill();
          context.strokeStyle = colors.playerStroke;
          context.lineWidth = Math.max(0.8, Math.min(1.5, cellSize * 0.07));
          context.stroke();
          if (cellSize >= 10) {
            context.beginPath();
            context.arc(centerX, centerY, Math.max(1, markerSize * 0.11), 0, Math.PI * 2);
            context.fillStyle = colors.playerStroke;
            context.fill();
          }
        }
      }

      context.strokeStyle = colors.boundary;
      context.lineWidth = 1;
      context.strokeRect(0.5, 0.5, Math.max(0, cssWidth - 1), Math.max(0, cssHeight - 1));
      if (zoomValue) zoomValue.textContent = Math.round(zoom * 100) + "%";
    }

    function onCanvasClick(event) {
      if (suppressNextClick) {
        suppressNextClick = false;
        if (suppressTimer) clearTimeout(suppressTimer);
        suppressTimer = null;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      if (typeof onCell !== "function") return;
      var rect = canvas.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      var x = Math.floor((event.clientX - rect.left) / rect.width * width);
      var y = Math.floor((event.clientY - rect.top) / rect.height * height);
      if (x < 0 || y < 0 || x >= width || y >= height) return;
      selectedCell = { x: x, y: y };
      render();
      onCell(x, y, groups[x + "," + y] || []);
    }

    function clearSelection() {
      if (!selectedCell) return;
      selectedCell = null;
      render();
      if (typeof onClear === "function") onClear();
    }

    function onViewportClick(event) {
      if (event.target === canvas) return;
      clearSelection();
    }

    function onOutsideClick(event) {
      var target = event.target;
      if (!target || !target.closest || target.closest(".tm-minimap-viewport, .tm-minimap-actions, button, input, select, textarea")) return;
      clearSelection();
    }

    function updateData(nextData) {
      var nextWidth = Number(nextData && nextData.width);
      var nextHeight = Number(nextData && nextData.height);
      if (!(nextWidth > 0) || !(nextHeight > 0)) return false;
      width = nextWidth;
      height = nextHeight;
      rows = nextData.rows || [];
      events = nextData.events || [];
      data = nextData;
      indexEvents();
      if (selectedCell && (selectedCell.x >= width || selectedCell.y >= height)) {
        selectedCell = null;
        if (typeof onClear === "function") onClear();
      }
      render();
      return true;
    }

    function onPointerDown(event) {
      if (event.pointerType === "mouse" && event.button !== 0) return;
      pointerState = {
        id: event.pointerId,
        x: event.clientX,
        y: event.clientY,
        startedAt: Date.now(),
        scrollLeft: viewport.scrollLeft,
        scrollTop: viewport.scrollTop,
        dragging: false
      };
      if (canvas.setPointerCapture) {
        try { canvas.setPointerCapture(event.pointerId); } catch (_) {}
      }
    }

    function onPointerMove(event) {
      if (!pointerState || pointerState.id !== event.pointerId) return;
      var deltaX = event.clientX - pointerState.x;
      var deltaY = event.clientY - pointerState.y;
      if (!pointerState.dragging && Math.max(Math.abs(deltaX), Math.abs(deltaY)) > 6) {
        pointerState.dragging = true;
        viewport.classList.add("tm-map-dragging");
      }
      if (!pointerState.dragging) return;
      viewport.scrollLeft = pointerState.scrollLeft - deltaX;
      viewport.scrollTop = pointerState.scrollTop - deltaY;
      event.preventDefault();
    }

    function suppressClickAfterDrag() {
      suppressNextClick = true;
      if (suppressTimer) clearTimeout(suppressTimer);
      suppressTimer = setTimeout(function () { suppressNextClick = false; suppressTimer = null; }, 300);
    }

    function finishPointer(event) {
      if (!pointerState || (event && pointerState.id !== event.pointerId)) return;
      if (pointerState.dragging || Date.now() - pointerState.startedAt > 500) suppressClickAfterDrag();
      viewport.classList.remove("tm-map-dragging");
      var pointerId = pointerState.id;
      pointerState = null;
      if (canvas.releasePointerCapture) {
        try { if (canvas.hasPointerCapture(pointerId)) canvas.releasePointerCapture(pointerId); } catch (_) {}
      }
    }

    function suppressContextMenu(event) { event.preventDefault(); }

    canvas.addEventListener("click", onCanvasClick);
    viewport.addEventListener("click", onViewportClick);
    if (viewport.parentElement) viewport.parentElement.addEventListener("click", onOutsideClick);
    canvas.addEventListener("pointerdown", onPointerDown);
    canvas.addEventListener("pointermove", onPointerMove);
    canvas.addEventListener("pointerup", finishPointer);
    canvas.addEventListener("pointercancel", finishPointer);
    canvas.addEventListener("contextmenu", suppressContextMenu);
    if (slider) slider.addEventListener("input", render);
    if (typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver(render);
      resizeObserver.observe(viewport);
    } else {
      resizeHandler = render;
      window.addEventListener("resize", resizeHandler);
    }
    render();

    return {
      redraw: render,
      updateData: updateData,
      destroy: function () {
        canvas.removeEventListener("click", onCanvasClick);
        viewport.removeEventListener("click", onViewportClick);
        if (viewport.parentElement) viewport.parentElement.removeEventListener("click", onOutsideClick);
        canvas.removeEventListener("pointerdown", onPointerDown);
        canvas.removeEventListener("pointermove", onPointerMove);
        canvas.removeEventListener("pointerup", finishPointer);
        canvas.removeEventListener("pointercancel", finishPointer);
        canvas.removeEventListener("contextmenu", suppressContextMenu);
        viewport.classList.remove("tm-map-dragging");
        if (suppressTimer) clearTimeout(suppressTimer);
        if (slider) slider.removeEventListener("input", render);
        if (resizeObserver) resizeObserver.disconnect();
        if (resizeHandler) window.removeEventListener("resize", resizeHandler);
      }
    };
  }

  window.TyranorMiniMap = { mount: mount };
})();
