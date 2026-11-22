param([string]$Out = "$PSScriptRoot\..\SpeedAlertIOS\Resources\Assets.xcassets\AppIcon.appiconset\icon-1024.png")
# Renders the 1024x1024 App Store icon from the Android vector app/src/main/res/drawable/ic_speed_alert.xml
# (viewport 108; WPF geometry syntax = SVG path syntax). Saved as 24-bit RGB: Apple rejects icons with alpha.
Add-Type -AssemblyName PresentationCore, PresentationFramework, WindowsBase
$ErrorActionPreference = 'Stop'

$paths = @(
  @{f='#112234'; d='M0,0h108v108h-108z'},
  @{s='#FFFFFF'; w=5; d='M20,73 A40,40 0 0 1 88,73'},
  @{s='#26DF53'; w=6; d='M20,71 A38,38 0 0 1 48,24'},
  @{s='#F63038'; w=6; d='M65,26 A38,38 0 0 1 88,71'},
  @{f='#F63038'; d='M49,61 L78,36 L58,67 Z'},
  @{f='#F7F9FA'; d='M54,51 a14,14 0 1 0 0,28 a14,14 0 1 0 0,-28'},
  @{f='#13212B'; d='M54,59 a6,6 0 1 0 0,12 a6,6 0 1 0 0,-12'},
  @{f='#FFFFFF'; d='M67,79 L86,49 L105,79 Q106,84 102,84 L70,84 Q65,84 67,79 Z'},
  @{f='#F23338'; d='M71,78 L86,54 L101,78 Q102,81 98,81 L74,81 Q70,81 71,78 Z'},
  @{f='#FFFFFF'; d='M84,62h4v11h-4z M84,75h4v3h-4z'}
)
function Brush($hex) { $b = [Windows.Media.SolidColorBrush]([Windows.Media.ColorConverter]::ConvertFromString($hex)); $b.Freeze(); $b }

$size = 1024
$v = [Windows.Media.DrawingVisual]::new(); $dc = $v.RenderOpen()
$dc.PushTransform([Windows.Media.ScaleTransform]::new($size/108, $size/108))
foreach ($p in $paths) {
  $g = [Windows.Media.Geometry]::Parse($p.d)
  if ($p.f) { $dc.DrawGeometry((Brush $p.f), $null, $g) }
  else {
    $pen = [Windows.Media.Pen]::new((Brush $p.s), $p.w)
    $pen.StartLineCap = 'Round'; $pen.EndLineCap = 'Round'
    $dc.DrawGeometry($null, $pen, $g)
  }
}
$dc.Pop(); $dc.Close()

$bmp = [Windows.Media.Imaging.RenderTargetBitmap]::new($size, $size, 96, 96, [Windows.Media.PixelFormats]::Pbgra32)
$bmp.Render($v)
$rgb = [Windows.Media.Imaging.FormatConvertedBitmap]::new($bmp, [Windows.Media.PixelFormats]::Bgr24, $null, 0)
$enc = [Windows.Media.Imaging.PngBitmapEncoder]::new()
$enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($rgb))
$fs = [IO.File]::Create($Out); try { $enc.Save($fs) } finally { $fs.Close() }
"ok $Out"
