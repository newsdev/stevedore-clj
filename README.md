# stevedore

Doesn't work yet, but this will hopefully sometime be a MUCH FASTER version of github.com/newsdev/stevedore-uploader.

## Installation

???

## Usage

FIXME: explanation

    $ java -jar stevedore-0.1.0-standalone.jar [args]

## Options

Upload documents to Stevedore, so they can be searchable: github.com/newsdev/stevedore

Usage: stevedore [options] input/file/path

Options:
  -h, --host SERVER:PORT  http://localhost:9200  the location of the ElasticSearch server
  -i, --index NAME                               a name to use for the ES index
  -s, --s3path NAME                              a name to use for the ES index
  -b, --s3bucket NAME                            a name to use for the ES index
      --slice-size NUM    100                    Process documents in batches of SLICE. Default is 100. Lower this if you get timeouts. Raise it to go faster.
  -o, --no-ocr                                   don't attempt to OCR any PDFs, even if they contain no text
  -v, --verbose                                  Verbose
  -H, --help                                     Show this help message

Please refer to the manual page for more information.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
