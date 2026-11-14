(function () {
  "use strict";

  var COLORS = {
    blocked: "#102c58",
    passable: "#0788b1",
    grid: "rgba(220,245,255,.2)",
    boundary: "#f4fbff",
    eventFill: "#f4f6f7",
    eventText: "#17212a",
    player: "#17c34a",
    teleport: "#d946ef"
  };

  function mount(options) {
    var canvas = options && options.canvas;
    var viewport = options && options.viewport;
    var data = options && options.data;
    var slider = options && options.slider;
    var zoomValue = options && options.zoomValue;
    var onCell = options && options.onCell;
    if (!canvas || !viewport || !data || !(Number(data.width) > 0) || !(Number(data.height) > 0)) return null;

    var width = Number(data.width);
    var height = Number(data.height);
    var rows = data.rows || [];
    var events = data.events || [];
    var cellSize = 1;
    var resizeHandler = null;
    var resizeObserver = null;
    var groups = Object.create(null);

    events.forEach(function (event) {
      var x = Number(event.x), y = Number(event.y);
      if (!Number.isInteger(x) || !Number.isInteger(y) || x < 0 || y < 0 || x >= width || y >= height) return;
      var key = x + "," + y;
      if (!groups[key]) groups[key] = [];
      groups[key].push(event);
    });

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
          context.fillStyle = row.charAt(x) === "1" ? COLORS.passable : COLORS.blocked;
          context.fillRect(x * cellSize, y * cellSize, cellSize + 0.25, cellSize + 0.25);
        }
      }

      context.beginPath();
      for (var column = 0; column <= width; column++) {
        var lineX = column * cellSize;
        context.moveTo(lineX, 0);
        context.lineTo(lineX, cssHeight);
      }
      for (var line = 0; line <= height; line++) {
        var lineY = line * cellSize;
        context.moveTo(0, lineY);
        context.lineTo(cssWidth, lineY);
      }
      context.strokeStyle = COLORS.grid;
      context.lineWidth = Math.max(0.45, Math.min(1, cellSize * 0.05));
      context.stroke();

      Object.keys(groups).forEach(function (key) {
        var markers = groups[key];
        var first = markers[0];
        var centerX = (Number(first.x) + 0.5) * cellSize;
        var centerY = (Number(first.y) + 0.5) * cellSize;
        var radius = Math.max(0.45, Math.min(cellSize * 0.42, 9));
        var hasTeleport = markers.some(function (event) { return !!(event.isTeleport || event.teleport); });

        if (hasTeleport) {
          context.beginPath();
          context.arc(centerX, centerY, radius, 0, Math.PI * 2);
          context.strokeStyle = COLORS.teleport;
          context.lineWidth = Math.max(1, Math.min(2.5, cellSize * 0.11));
          context.stroke();
        }

        context.beginPath();
        context.arc(centerX, centerY, Math.max(0.35, radius * (hasTeleport ? 0.7 : 0.82)), 0, Math.PI * 2);
        context.fillStyle = COLORS.eventFill;
        context.fill();
        context.strokeStyle = "rgba(10,26,40,.65)";
        context.lineWidth = Math.max(0.5, Math.min(1.2, cellSize * 0.05));
        context.stroke();

        if (cellSize >= 7) {
          context.fillStyle = COLORS.eventText;
          context.font = "600 " + Math.max(5, Math.min(12, cellSize * 0.62)) + "px system-ui,sans-serif";
          context.textAlign = "center";
          context.textBaseline = "middle";
          context.fillText(String(first.id), centerX, centerY + 0.3);
        }
        if (markers.length > 1 && cellSize >= 14) {
          context.fillStyle = COLORS.eventText;
          context.font = "600 " + Math.max(5, Math.min(8, cellSize * 0.35)) + "px system-ui,sans-serif";
          context.textAlign = "right";
          context.fillText("+" + (markers.length - 1), centerX + radius, centerY - radius);
        }
      });

      if (data.player) {
        var playerX = Number(data.player.x), playerY = Number(data.player.y);
        if (playerX >= 0 && playerY >= 0 && playerX < width && playerY < height) {
          var inset = Math.max(0.5, cellSize * 0.13);
          context.fillStyle = COLORS.player;
          context.fillRect(playerX * cellSize + inset, playerY * cellSize + inset,
            Math.max(0, cellSize - inset * 2), Math.max(0, cellSize - inset * 2));
        }
      }

      context.strokeStyle = COLORS.boundary;
      context.lineWidth = 1;
      context.strokeRect(0.5, 0.5, Math.max(0, cssWidth - 1), Math.max(0, cssHeight - 1));
      if (zoomValue) zoomValue.textContent = Math.round(zoom * 100) + "%";
    }

    function onCanvasClick(event) {
      if (typeof onCell !== "function") return;
      var rect = canvas.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      var x = Math.floor((event.clientX - rect.left) / rect.width * width);
      var y = Math.floor((event.clientY - rect.top) / rect.height * height);
      if (x < 0 || y < 0 || x >= width || y >= height) return;
      onCell(x, y, groups[x + "," + y] || []);
    }

    canvas.addEventListener("click", onCanvasClick);
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
      destroy: function () {
        canvas.removeEventListener("click", onCanvasClick);
        if (slider) slider.removeEventListener("input", render);
        if (resizeObserver) resizeObserver.disconnect();
        if (resizeHandler) window.removeEventListener("resize", resizeHandler);
      }
    };
  }

  window.TyranorMiniMap = { mount: mount };
})();
