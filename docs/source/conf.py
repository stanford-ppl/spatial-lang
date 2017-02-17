import os
import sys

from sphinx.highlighting import PygmentsBridge
from pygments.formatters.latex import LatexFormatter
class CustomLatexFormatter(LatexFormatter):
  def __init__(self, **options):
    super(CustomLatexFormatter, self).__init__(**options)
    self.verboptions = r"formatcom=\footnotesize" 
PygmentsBridge.latex_formatter = CustomLatexFormatter

templates_path = ['_templates']
source_suffix = '.rst'
master_doc = 'index'
project = u'Spatial'
copyright = u'2017,Stanford PPL'
version = '0.1'
release = '0.1'
highlight_language = 'Scala' ## Name here is the name field of the Lexer, not the class name


#### HTML Info ###
html_theme = 'sphinx_rtd_theme'
html_static_path = ['_static']
htmlhelp_basename = 'SpatialDoc'

### LaTex Info ###
latex_documents = [
  ('index', 'Spatial.tex', u'Spatial Documentation',
  u'Stanford PPL', 'manual'),
]
latex_elements = {
  'classoptions': ',openany,oneside',
  'babel': '\\usepackage[english]{babel}'
}

### Manpage Info ###
man_pages = [
  ('index', 'spatial', u'Spatial Documentation',
  [u'Stanford PPL'], 1)
]

### TexInfo ###
texinfo_documents = [
  ('index', 'Spatial', u'Spatial Documentation',
  u'Stanford PPL', 'Spatial', 'One line description of project.',
  'Miscellaneous'),
]
