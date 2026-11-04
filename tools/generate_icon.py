"""Generate themed Android vectors and a dark SVG from jmx-icon.svg (stdlib only)."""

from copy import deepcopy
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)


def attr(name):
    return f"{{{ANDROID}}}{name}"


def color(value, opacity="1"):
    if float(opacity) == 1:
        return value
    return f"#{round(float(opacity) * 255):02X}{value[1:]}"


source = ET.parse(ROOT / "design/jmx-icon.svg").getroot()
gradients = {node.attrib["id"]: node for node in source.find(f"{SVG}defs")}
background = source.find(f"{SVG}g[@id='background']")
book = source.find(f"{SVG}g[@id='book']")


def append_path(parent, node, themed):
    values = {attr("pathData"): node.attrib["d"]}
    for svg_name, android_name in (
        ("fill-opacity", "fillAlpha"), ("stroke-opacity", "strokeAlpha"),
        ("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap"),
        ("stroke-linejoin", "strokeLineJoin"),
    ):
        if svg_name in node.attrib:
            values[attr(android_name)] = node.attrib[svg_name]
    if node.attrib.get("fill-rule") == "evenodd":
        values[attr("fillType")] = "evenOdd"
    path = ET.SubElement(parent, "path", values)
    for paint in ("fill", "stroke"):
        value = node.attrib.get(paint, "none")
        target = f"{paint}Color"
        if value.startswith("url(#"):
            gradient = gradients[value[5:-1]]
            wrapper = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": f"android:{target}"})
            grad = ET.SubElement(wrapper, "gradient", {
                attr("type"): "linear",
                attr("startX"): gradient.attrib["x1"],
                attr("startY"): gradient.attrib["y1"],
                attr("endX"): gradient.attrib["x2"],
                attr("endY"): gradient.attrib["y2"],
            })
            for stop in gradient:
                ET.SubElement(grad, "item", {
                    attr("offset"): stop.attrib.get("offset", "0"),
                    attr("color"): color(stop.attrib["stop-color"], stop.attrib.get("stop-opacity", "1")),
                })
        elif value != "none":
            path.set(attr(target), value)
    if not themed:
        return
    if node.attrib.get("data-role") == "surface":
        path.set(attr("fillColor"), "@color/jmx_icon_surface")
    elif node.attrib.get("data-role") == "rim":
        path.set(attr("strokeColor"), "@color/jmx_icon_rim")
        path.attrib.pop(attr("strokeAlpha"), None)


def write_vector(name, nodes, adaptive=False, background_layer=False, themed=True):
    size = "108" if adaptive else "512"
    vector = ET.Element("vector", {
        attr("width"): f"{size}dp", attr("height"): f"{size}dp",
        attr("viewportWidth"): size, attr("viewportHeight"): size,
    })
    parent = vector
    if adaptive:
        # The book stays inside the central safe area of launcher masks.
        scale, translate = ("0.2109375", "0") if background_layer else ("0.19", "5.36")
        parent = ET.SubElement(vector, "group", {
            attr("scaleX"): scale, attr("scaleY"): scale,
            attr("translateX"): translate, attr("translateY"): translate,
        })
    for node in nodes:
        append_path(parent, node, themed)
    ET.indent(vector, space="    ")
    target = RES / "drawable" / name
    target.write_text(ET.tostring(vector, encoding="unicode", xml_declaration=True) + "\n", encoding="utf-8")


write_vector("jmx_logo.xml", [*background, *book], themed=False)
write_vector("jmx_icon_foreground.xml", book, adaptive=True)
solid_background = ET.Element("path", {
    "d": "M0 0H512V512H0Z", "fill": "#FFFFFF", "data-role": "surface",
})
write_vector("jmx_icon_background.xml", [solid_background], adaptive=True, background_layer=True)
silhouette = deepcopy(book.find(f"{SVG}path[@id='silhouette']"))
silhouette.set("fill", "#000000")
silhouette.set("fill-rule", "evenodd")
cutouts = [node.attrib["d"] for node in book if node.attrib.get("data-panel") == "true"]
cutouts += [
    "M252 186H260V355H252Z",
    "M282 220Q318 205 353 207V214Q318 212 282 227Z",
    "M282 246Q318 231 353 233V240Q318 238 282 253Z",
    "M282 272Q306 262 331 261V268Q306 269 282 279Z",
]
silhouette.set("d", silhouette.attrib["d"] + "".join(cutouts))
write_vector("jmx_icon_monochrome.xml", [silhouette], adaptive=True)

for folder, surface, rim in (("values", "#FFFFFF", "#0F000000"), ("values-night", "#000000", "#1AFFFFFF")):
    resources = ET.Element("resources")
    ET.SubElement(resources, "color", {"name": "jmx_icon_surface"}).text = surface
    ET.SubElement(resources, "color", {"name": "jmx_icon_rim"}).text = rim
    ET.indent(resources, space="    ")
    (RES / folder / "icon_colors.xml").write_text(
        ET.tostring(resources, encoding="unicode", xml_declaration=True) + "\n", encoding="utf-8",
    )

dark_source = deepcopy(source)
dark_source.find(f"{SVG}title").text = "JMComicX — Apricot on Black"
dark_source.find(f"{SVG}desc").text = "An apricot comic book on black, with layered pages and pale cream-orange panel details."
for node in dark_source.iter():
    if node.attrib.get("data-role") == "surface":
        node.set("fill", "#000000")
    elif node.attrib.get("data-role") == "rim":
        node.set("stroke", "#FFFFFF")
        node.set("stroke-opacity", "0.10")
dark_background = dark_source.find(f"{SVG}g[@id='background']")
dark_book = dark_source.find(f"{SVG}g[@id='book']")
# Compose can override the app theme independently of Android's configuration.
write_vector("jmx_logo_dark.xml", [*dark_background, *dark_book], themed=False)
ET.register_namespace("", SVG[1:-1])
ET.indent(dark_source, space="  ")
(ROOT / "design/jmx-icon-dark.svg").write_text(
    ET.tostring(dark_source, encoding="unicode") + "\n", encoding="utf-8",
)
print("Generated five Android vectors, light/dark colors and dark SVG")
