from pathlib import Path

p=Path('src/com/wtm/ui/ThemedFileChooser.java')
text=p.read_text()
text=text.replace('import javax.swing.filechooser.FileNameExtensionFilter;\n','')
start=text.index('    public static JFileChooser chooseVideo(Component parent){')
end=text.index('    private static final class StyledChooser',start)
text=text[:start]+text[end:]
p.write_text(text)
